package com.notarist.auth.application.handler.command;

import com.notarist.auth.application.command.AuthenticateCommand;
import com.notarist.auth.application.port.in.AuthenticateUserUseCase;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.application.service.JwtService;
import com.notarist.auth.application.service.PasswordVerifier;
import com.notarist.auth.api.response.TokenResponse;
import com.notarist.auth.domain.model.Role;
import com.notarist.auth.domain.model.Session;
import com.notarist.auth.domain.model.User;
import com.notarist.auth.domain.service.RefreshTokenFactory;
import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthenticateUserHandler implements AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final SessionTokenRepository sessionTokenRepository;
    private final JwtService jwtService;
    private final PasswordVerifier passwordVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final long refreshTokenTtlSeconds;

    public AuthenticateUserHandler(
            UserRepository userRepository,
            SessionTokenRepository sessionTokenRepository,
            JwtService jwtService,
            PasswordVerifier passwordVerifier,
            ApplicationEventPublisher eventPublisher,
            @Value("${notarist.auth.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds) {
        this.userRepository = userRepository;
        this.sessionTokenRepository = sessionTokenRepository;
        this.jwtService = jwtService;
        this.passwordVerifier = passwordVerifier;
        this.eventPublisher = eventPublisher;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    public TokenResponse execute(AuthenticateCommand command) {
        User user = userRepository.findByUsername(command.username())
                .orElse(null);

        if (user == null) {
            // An attempt against a username that does not exist is still a failed login, and it is
            // the shape credential-stuffing takes. Auditing only the "user exists but password is
            // wrong" case would leave the most common brute-force pattern with no trace at all.
            // No actor/tenant can be attributed — audit_trail permits NULL for both.
            publishUnknownUserLoginFailure(command);
            throw new UnauthorizedAccessException(
                    "AUTH_INVALID_CREDENTIALS", "Invalid username or password");
        }

        if (!user.isActive()) {
            publishLoginFailure(command, user, "ACCOUNT_DISABLED");
            throw new UnauthorizedAccessException("AUTH_ACCOUNT_DISABLED", "Account is disabled");
        }

        if (!passwordVerifier.matches(command.password(), user.getPasswordHash())) {
            publishLoginFailure(command, user, "INVALID_PASSWORD");
            throw new UnauthorizedAccessException("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
        }

        String accessToken = jwtService.issueAccessToken(user, command.correlationId());
        String opaqueRefreshToken = RefreshTokenFactory.generateOpaqueToken();
        Session session = RefreshTokenFactory.createSession(
                user.getUserId(), user.getTenantId(), opaqueRefreshToken, refreshTokenTtlSeconds);
        sessionTokenRepository.save(session);

        publishLoginSuccess(command, user);

        List<String> roles = user.getRoles().stream().map(Role::name).collect(Collectors.toList());
        return new TokenResponse(
                accessToken,
                opaqueRefreshToken,
                "Bearer",
                (int) jwtService.getAccessTokenTtlSeconds(),
                user.getUserId().value(),
                roles,
                user.getTenantId(),
                session.getSessionId().value()
        );
    }

    private void publishLoginSuccess(AuthenticateCommand cmd, User user) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "AUTH_LOGIN_SUCCESS", "USER", user.getUserId().value().toString(),
                user.getUserId().value(),
                user.getRoles().stream().map(Enum::name).findFirst().orElse("UNKNOWN"),
                user.getTenantId(), "LOGIN", "SUCCESS", cmd.ipAddress(),
                cmd.correlationId().value(), Map.of("username", user.getUsername())
        ));
    }

    /**
     * Failed login against a username with no matching user. The attempted username is recorded as
     * the subject (it is the only identifying detail available) while actor and tenant stay null —
     * attributing an unauthenticated attempt to a user or tenant would be a fabrication.
     */
    private void publishUnknownUserLoginFailure(AuthenticateCommand cmd) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "AUTH_LOGIN_FAILURE", "USER", cmd.username(),
                null,
                null,
                null, "LOGIN", "FAILURE", cmd.ipAddress(),
                cmd.correlationId().value(), Map.of("reason", "USER_NOT_FOUND")
        ));
    }

    private void publishLoginFailure(AuthenticateCommand cmd, User user, String reason) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "AUTH_LOGIN_FAILURE", "USER", user.getUserId().value().toString(),
                user.getUserId().value(),
                user.getRoles().stream().map(Enum::name).findFirst().orElse("UNKNOWN"),
                user.getTenantId(), "LOGIN", "FAILURE", cmd.ipAddress(),
                cmd.correlationId().value(), Map.of("reason", reason)
        ));
    }
}
