package com.notarist.auth.application.handler.command;

import com.notarist.auth.application.command.RefreshTokenCommand;
import com.notarist.auth.application.port.in.RefreshTokenUseCase;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.application.service.JwtService;
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
public class RefreshTokenHandler implements RefreshTokenUseCase {

    private final SessionTokenRepository sessionTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenHandler(
            SessionTokenRepository sessionTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            ApplicationEventPublisher eventPublisher,
            @Value("${notarist.auth.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds) {
        this.sessionTokenRepository = sessionTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    public TokenResponse execute(RefreshTokenCommand command) {
        String tokenHash = RefreshTokenFactory.hashToken(command.refreshToken());

        Session existing = sessionTokenRepository.findByRefreshTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "AUTH_INVALID_REFRESH_TOKEN", "Refresh token not found or already used"));

        if (!existing.isValid()) {
            throw new UnauthorizedAccessException(
                    "AUTH_EXPIRED_REFRESH_TOKEN", "Refresh token expired or invalidated");
        }

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new UnauthorizedAccessException(
                        "AUTH_USER_NOT_FOUND", "User no longer exists"));

        if (!user.isActive()) {
            throw new UnauthorizedAccessException("AUTH_ACCOUNT_DISABLED", "Account is disabled");
        }

        // Atomically consume the presented refresh token before issuing new credentials.
        // This is the transaction boundary for rotation: if another request already rotated
        // this token (concurrent refresh or replay), the compare-and-set affects 0 rows and
        // we reject here — preventing a token-reuse double-issue race.
        if (!sessionTokenRepository.invalidateIfActive(existing.getSessionId())) {
            throw new UnauthorizedAccessException(
                    "AUTH_REFRESH_TOKEN_REUSED",
                    "Refresh token already used or rotated concurrently");
        }

        String newAccessToken = jwtService.issueAccessToken(user, command.correlationId());
        String newOpaqueToken = RefreshTokenFactory.generateOpaqueToken();
        Session newSession = RefreshTokenFactory.createSession(
                user.getUserId(), user.getTenantId(), newOpaqueToken, refreshTokenTtlSeconds);
        sessionTokenRepository.save(newSession);

        eventPublisher.publishEvent(new AuditEventPayload(
                "AUTH_TOKEN_REFRESH", "SESSION", newSession.getSessionId().value().toString(),
                user.getUserId().value(),
                user.getRoles().stream().map(Enum::name).findFirst().orElse("UNKNOWN"),
                user.getTenantId(), "TOKEN_REFRESH", "SUCCESS", command.ipAddress(),
                command.correlationId().value(),
                Map.of("userId", user.getUserId().value().toString())
        ));

        List<String> roles = user.getRoles().stream().map(Role::name).collect(Collectors.toList());
        return new TokenResponse(
                newAccessToken,
                newOpaqueToken,
                "Bearer",
                (int) jwtService.getAccessTokenTtlSeconds(),
                user.getUserId().value(),
                roles,
                user.getTenantId(),
                newSession.getSessionId().value()
        );
    }
}
