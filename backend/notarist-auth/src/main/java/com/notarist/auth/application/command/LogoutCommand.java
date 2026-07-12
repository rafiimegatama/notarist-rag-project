package com.notarist.auth.application.command;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.SessionId;

import java.util.UUID;

public record LogoutCommand(
    SessionId sessionId,
    UUID callerUserId,
    String jti,
    long remainingTokenValiditySeconds,
    CorrelationId correlationId
) {}
