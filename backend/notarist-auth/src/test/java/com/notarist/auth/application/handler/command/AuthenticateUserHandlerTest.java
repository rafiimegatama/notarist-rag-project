package com.notarist.auth.application.handler.command;

import com.notarist.auth.application.command.AuthenticateCommand;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.application.service.PasswordVerifier;
import com.notarist.auth.api.response.TokenResponse;
import com.notarist.auth.domain.model.Role;
import com.notarist.auth.domain.model.Session;
import com.notarist.auth.domain.model.User;
import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.PersonId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticateUserHandlerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final PersonId USER_ID = PersonId.generate();

    private UserRepository userRepository;
    private SessionTokenRepository sessionTokenRepository;
    private JwtService jwtService;
    private PasswordVerifier passwordVerifier;
    private ApplicationEventPublisher eventPublisher;
    private AuthenticateUserHandler handler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        sessionTokenRepository = mock(SessionTokenRepository.class);
        jwtService = mock(JwtService.class);
        passwordVerifier = mock(PasswordVerifier.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(jwtService.issueAccessToken(any(), any())).thenReturn("signed.jwt.token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        handler = new AuthenticateUserHandler(
                userRepository, sessionTokenRepository, jwtService,
                passwordVerifier, eventPublisher, 604800L);
    }

    private static User user(boolean active) {
        return new User(USER_ID, "budi", "$2a$12$hash", "Budi Santoso",
                Set.of(Role.NOTARIS), TENANT, active, Instant.now());
    }

    private static AuthenticateCommand command() {
        return new AuthenticateCommand("budi", "correct-horse", "10.0.0.9",
                "junit", CorrelationId.generate());
    }

    private List<AuditEventPayload> publishedAuditEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(AuditEventPayload.class::isInstance)
                .map(AuditEventPayload.class::cast)
                .toList();
    }

    @Test
    @DisplayName("valid credentials issue a token, persist a session, and audit the success")
    void loginSuccess() {
        when(userRepository.findByUsername("budi")).thenReturn(Optional.of(user(true)));
        when(passwordVerifier.matches(anyString(), anyString())).thenReturn(true);

        TokenResponse response = handler.execute(command());

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tenantId()).isEqualTo(TENANT);

        // The refresh token must be persisted as a session, and only ever as a hash.
        ArgumentCaptor<Session> session = ArgumentCaptor.forClass(Session.class);
        verify(sessionTokenRepository).save(session.capture());
        assertThat(session.getValue().getRefreshTokenHash())
                .as("refresh token must never be stored in plaintext")
                .isNotEqualTo(response.refreshToken());
        assertThat(session.getValue().getTenantId()).isEqualTo(TENANT);

        assertThat(publishedAuditEvents())
                .extracting(AuditEventPayload::eventType)
                .containsExactly("AUTH_LOGIN_SUCCESS");
    }

    @Test
    @DisplayName("wrong password is rejected, audited as a failure, and issues no token")
    void wrongPasswordIsRejectedAndAudited() {
        when(userRepository.findByUsername("budi")).thenReturn(Optional.of(user(true)));
        when(passwordVerifier.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.execute(command()))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(sessionTokenRepository, never()).save(any());
        assertThat(publishedAuditEvents())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.eventType()).isEqualTo("AUTH_LOGIN_FAILURE");
                    assertThat(e.outcome()).isEqualTo("FAILURE");
                    assertThat(e.detail()).containsEntry("reason", "INVALID_PASSWORD");
                });
    }

    /**
     * Regression guard: the handler used to throw on orElseThrow BEFORE publishing anything, so a
     * login attempt against a username that does not exist — the shape credential-stuffing takes —
     * left no audit trace at all.
     */
    @Test
    @DisplayName("unknown username is audited as a login failure (not silently dropped)")
    void unknownUsernameIsAudited() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        AuthenticateCommand cmd = new AuthenticateCommand("ghost", "whatever", "10.0.0.9",
                "junit", CorrelationId.generate());

        assertThatThrownBy(() -> handler.execute(cmd))
                .isInstanceOf(UnauthorizedAccessException.class);

        assertThat(publishedAuditEvents())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.eventType()).isEqualTo("AUTH_LOGIN_FAILURE");
                    assertThat(e.detail()).containsEntry("reason", "USER_NOT_FOUND");
                    // An unauthenticated attempt cannot be attributed to a user or a tenant.
                    assertThat(e.actorUserId()).isNull();
                    assertThat(e.tenantId()).isNull();
                    assertThat(e.subjectId()).isEqualTo("ghost");
                });
    }

    @Test
    @DisplayName("a disabled account cannot log in, and the attempt is audited")
    void disabledAccountIsRejected() {
        when(userRepository.findByUsername("budi")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> handler.execute(command()))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(sessionTokenRepository, never()).save(any());
        // A disabled account must be rejected before the password is even considered.
        verify(passwordVerifier, never()).matches(anyString(), anyString());
        assertThat(publishedAuditEvents())
                .singleElement()
                .satisfies(e -> assertThat(e.detail()).containsEntry("reason", "ACCOUNT_DISABLED"));
    }
}
