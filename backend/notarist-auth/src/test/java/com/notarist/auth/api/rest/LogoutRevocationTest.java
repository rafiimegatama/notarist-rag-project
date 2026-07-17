package com.notarist.auth.api.rest;

import com.notarist.auth.application.command.LogoutCommand;
import com.notarist.auth.application.handler.command.LogoutHandler;
import com.notarist.auth.application.port.in.AuthenticateUserUseCase;
import com.notarist.auth.application.port.in.InvalidateSessionUseCase;
import com.notarist.auth.application.port.in.RefreshTokenUseCase;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.domain.model.Session;
import com.notarist.auth.domain.service.RefreshTokenFactory;
import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.security.VpdContextHolder;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Revocation on logout, and the ownership rules around it.
 *
 * <p>Regression guard for a CRITICAL finding: {@code jti} and {@code remainingTtlSeconds} used to
 * be {@code @RequestParam}s passed straight to the deny-list with no ownership check. Any
 * authenticated caller could therefore revoke a token whose jti they knew, and could insert
 * arbitrary jti values with far-future TTLs (the purge only reclaims *expired* rows). Because both
 * were optional and the TTL defaulted to 0, a client that simply omitted them also got a logout
 * that revoked nothing at all.
 */
class LogoutRevocationTest {

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final String CALLER_JTI = UUID.randomUUID().toString();

    private InvalidateSessionUseCase invalidateSessionUseCase;
    private JwtService jwtService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        invalidateSessionUseCase = mock(InvalidateSessionUseCase.class);
        jwtService = mock(JwtService.class);
        controller = new AuthController(
                mock(AuthenticateUserUseCase.class),
                mock(RefreshTokenUseCase.class),
                invalidateSessionUseCase,
                jwtService);

        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(CALLER_JTI);
        when(claims.getExpiration())
                .thenReturn(Date.from(Instant.now().plusSeconds(600)));
        when(jwtService.validateAndParseClaims("caller.jwt")).thenReturn(claims);

        VpdContextHolder.set(new VpdContextHolder.VpdPrincipal(CALLER_ID, TENANT, "NOTARIS"));
    }

    @AfterEach
    void tearDown() {
        VpdContextHolder.clear();
    }

    private static MockHttpServletRequest logoutRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer caller.jwt");
        return request;
    }

    @Test
    @DisplayName("logout revokes the caller's OWN jti, taken from the bearer token")
    void logoutRevokesCallersOwnToken() {
        UUID sessionId = UUID.randomUUID();

        controller.logout(sessionId, logoutRequest());

        ArgumentCaptor<LogoutCommand> command = ArgumentCaptor.forClass(LogoutCommand.class);
        verify(invalidateSessionUseCase).execute(command.capture());

        assertThat(command.getValue().jti())
                .as("the revoked jti must come from the caller's validated token")
                .isEqualTo(CALLER_JTI);
        assertThat(command.getValue().callerUserId()).isEqualTo(CALLER_ID);
        assertThat(command.getValue().remainingTokenValiditySeconds())
                .as("TTL must be derived from the token's own exp, not supplied by the client")
                .isBetween(1L, 600L);
    }

    /**
     * The attack the CRITICAL fix closes: a caller supplies someone else's jti (and a far-future
     * TTL) as query parameters. Those parameters no longer exist, so even when sent they are
     * ignored and the caller's own token is the only thing revoked.
     */
    @Test
    @DisplayName("a client-supplied jti/TTL cannot influence what gets revoked")
    void clientSuppliedJtiIsIgnored() {
        String victimJti = UUID.randomUUID().toString();
        MockHttpServletRequest request = logoutRequest();
        request.addParameter("jti", victimJti);
        request.addParameter("remainingTtlSeconds", "315360000");  // 10 years

        controller.logout(UUID.randomUUID(), request);

        ArgumentCaptor<LogoutCommand> command = ArgumentCaptor.forClass(LogoutCommand.class);
        verify(invalidateSessionUseCase).execute(command.capture());

        assertThat(command.getValue().jti())
                .as("a caller must never be able to revoke another user's token")
                .isEqualTo(CALLER_JTI)
                .isNotEqualTo(victimJti);
        assertThat(command.getValue().remainingTokenValiditySeconds())
                .as("an attacker-chosen TTL would let the deny-list be grown without bound")
                .isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("the handler deny-lists the jti for the token's remaining life")
    void handlerAddsJtiToDenyList() {
        SessionTokenRepository sessions = mock(SessionTokenRepository.class);
        TokenDenyListPort denyList = mock(TokenDenyListPort.class);
        LogoutHandler handler = new LogoutHandler(sessions, denyList, mock(ApplicationEventPublisher.class));

        PersonId callerPersonId = PersonId.of(CALLER_ID);
        Session session = RefreshTokenFactory.createSession(
                callerPersonId, TENANT, RefreshTokenFactory.generateOpaqueToken(), 604800L);
        when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));

        handler.execute(new LogoutCommand(
                session.getSessionId(), CALLER_ID, CALLER_JTI, 600L, CorrelationId.generate()));

        verify(sessions).invalidate(session.getSessionId());
        verify(denyList).addToDenyList(CALLER_JTI, Duration.ofSeconds(600));
    }

    @Test
    @DisplayName("a caller cannot log out another user's session, and the attempt is audited")
    void cannotLogoutAnotherUsersSession() {
        SessionTokenRepository sessions = mock(SessionTokenRepository.class);
        TokenDenyListPort denyList = mock(TokenDenyListPort.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        LogoutHandler handler = new LogoutHandler(sessions, denyList, events);

        Session victimSession = RefreshTokenFactory.createSession(
                PersonId.generate(), TENANT, RefreshTokenFactory.generateOpaqueToken(), 604800L);
        when(sessions.findById(victimSession.getSessionId())).thenReturn(Optional.of(victimSession));

        assertThatThrownBy(() -> handler.execute(new LogoutCommand(
                victimSession.getSessionId(), CALLER_ID, CALLER_JTI, 600L, CorrelationId.generate())))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(sessions, never()).invalidate(any());
        verify(denyList, never()).addToDenyList(anyString(), any());

        // Attempting to terminate someone else's session must leave a trace.
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        AuditEventPayload denial = (AuditEventPayload) published.getValue();
        assertThat(denial.eventType()).isEqualTo("SECURITY_ACCESS_DENIED");
        assertThat(denial.outcome()).isEqualTo("FAILURE");
        assertThat(denial.actorUserId()).isEqualTo(CALLER_ID);
        assertThat(denial.detail()).containsEntry("reason", "SESSION_OWNERSHIP_VIOLATION");
    }
}
