package com.notarist.auth.application.handler.command;

import com.notarist.auth.application.command.LogoutCommand;
import com.notarist.auth.application.port.in.InvalidateSessionUseCase;
import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.application.port.out.TokenDenyListPort;
import com.notarist.auth.domain.model.Session;
import com.notarist.core.api.audit.AuditEventPayload;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class LogoutHandler implements InvalidateSessionUseCase {

    private final SessionTokenRepository sessionTokenRepository;
    private final TokenDenyListPort tokenDenyListPort;
    private final ApplicationEventPublisher eventPublisher;

    public LogoutHandler(
            SessionTokenRepository sessionTokenRepository,
            TokenDenyListPort tokenDenyListPort,
            ApplicationEventPublisher eventPublisher) {
        this.sessionTokenRepository = sessionTokenRepository;
        this.tokenDenyListPort = tokenDenyListPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(LogoutCommand command) {
        Session session = sessionTokenRepository.findById(command.sessionId())
                .orElse(null);

        sessionTokenRepository.invalidate(command.sessionId());

        if (command.jti() != null && command.remainingTokenValiditySeconds() > 0) {
            tokenDenyListPort.addToDenyList(
                    command.jti(),
                    Duration.ofSeconds(command.remainingTokenValiditySeconds()));
        }

        if (session != null) {
            eventPublisher.publishEvent(new AuditEventPayload(
                    "AUTH_LOGOUT", "SESSION", session.getSessionId().value().toString(),
                    session.getUserId().value(), "UNKNOWN",
                    session.getTenantId(), "LOGOUT", "SUCCESS", null,
                    command.correlationId().value(),
                    Map.of("sessionId", session.getSessionId().value().toString())
            ));
        }
    }
}
