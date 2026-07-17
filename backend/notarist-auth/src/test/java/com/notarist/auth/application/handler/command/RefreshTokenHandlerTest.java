package com.notarist.auth.application.handler.command;

import com.notarist.auth.application.command.RefreshTokenCommand;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.domain.model.Role;
import com.notarist.auth.domain.model.Session;
import com.notarist.auth.domain.model.User;
import com.notarist.auth.domain.service.RefreshTokenFactory;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.domain.valueobject.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenHandlerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final PersonId USER_ID = PersonId.generate();

    private SessionTokenRepository sessionTokenRepository;
    private UserRepository userRepository;
    private JwtService jwtService;
    private RefreshTokenHandler handler;

    private String opaqueToken;
    private Session activeSession;

    @BeforeEach
    void setUp() {
        sessionTokenRepository = mock(SessionTokenRepository.class);
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        when(jwtService.issueAccessToken(any(), any())).thenReturn("new.access.token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        opaqueToken = RefreshTokenFactory.generateOpaqueToken();
        activeSession = RefreshTokenFactory.createSession(USER_ID, TENANT, opaqueToken, 604800L);

        User user = new User(USER_ID, "budi", "$2a$12$hash", "Budi Santoso",
                Set.of(Role.NOTARIS), TENANT, true, Instant.now());

        when(sessionTokenRepository.findByRefreshTokenHash(RefreshTokenFactory.hashToken(opaqueToken)))
                .thenReturn(Optional.of(activeSession));
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT)).thenReturn(Optional.of(user));

        handler = new RefreshTokenHandler(
                sessionTokenRepository, userRepository, jwtService, eventPublisher, 604800L);
    }

    private RefreshTokenCommand command() {
        return new RefreshTokenCommand(opaqueToken, "10.0.0.9", CorrelationId.generate());
    }

    @Test
    @DisplayName("a valid refresh rotates the token: old session consumed, new session issued")
    void refreshRotatesToken() {
        when(sessionTokenRepository.invalidateIfActive(activeSession.getSessionId())).thenReturn(true);

        var response = handler.execute(command());

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken())
                .as("rotation must hand back a NEW refresh token, not the consumed one")
                .isNotEqualTo(opaqueToken);

        verify(sessionTokenRepository).invalidateIfActive(activeSession.getSessionId());
        verify(sessionTokenRepository).save(any(Session.class));
    }

    /**
     * The user lookup must be tenant-scoped. /auth/refresh is permitAll, so there is no principal
     * and no VPD context; a bare id lookup would be hidden by the fail-closed tenant policy and
     * would break refresh for every user. The tenant comes from the validated session row.
     */
    @Test
    @DisplayName("the user lookup is scoped to the tenant carried on the validated session")
    void userLookupIsTenantScoped() {
        when(sessionTokenRepository.invalidateIfActive(any())).thenReturn(true);

        handler.execute(command());

        verify(userRepository).findByIdAndTenantId(USER_ID, TENANT);
    }

    /**
     * Replay: the compare-and-set has already consumed this token, so invalidateIfActive reports
     * that it did not win the row. No new credential may be issued.
     */
    @Test
    @DisplayName("replaying an already-consumed refresh token is rejected and issues nothing")
    void replayedTokenIsRejected() {
        when(sessionTokenRepository.invalidateIfActive(activeSession.getSessionId())).thenReturn(false);

        assertThatThrownBy(() -> handler.execute(command()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("already used");

        verify(sessionTokenRepository, never()).save(any());
        verify(jwtService, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("an invalidated session is rejected before the CAS is even attempted")
    void invalidatedSessionIsRejected() {
        activeSession.invalidate();

        assertThatThrownBy(() -> handler.execute(command()))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(sessionTokenRepository, never()).invalidateIfActive(any());
    }

    @Test
    @DisplayName("an unknown refresh token is rejected")
    void unknownTokenIsRejected() {
        when(sessionTokenRepository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.execute(command()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    /**
     * Concurrency: two requests present the SAME refresh token at once. The real defence is the
     * atomic UPDATE ... WHERE invalidated = false, so this stubs invalidateIfActive with an
     * AtomicBoolean CAS — the same semantics the SQL provides — and asserts exactly one caller
     * wins. Before the CAS existed (a read-then-write), both could have been issued tokens.
     */
    @Test
    @DisplayName("concurrent refresh of the same token: exactly one wins, the other is rejected")
    void concurrentRefreshIssuesExactlyOneToken() throws Exception {
        AtomicBoolean consumed = new AtomicBoolean(false);
        when(sessionTokenRepository.invalidateIfActive(activeSession.getSessionId()))
                .thenAnswer(inv -> consumed.compareAndSet(false, true));

        int threads = 8;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger issued = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            Callable<Void> attempt = () -> {
                startTogether.await(5, TimeUnit.SECONDS);
                try {
                    handler.execute(command());
                    issued.incrementAndGet();
                } catch (UnauthorizedAccessException expected) {
                    rejected.incrementAndGet();
                }
                return null;
            };

            for (Future<Void> f : pool.invokeAll(java.util.Collections.nCopies(threads, attempt))) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(issued.get()).as("exactly one concurrent refresh may succeed").isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
        verify(sessionTokenRepository, org.mockito.Mockito.times(1)).save(any(Session.class));
    }
}
