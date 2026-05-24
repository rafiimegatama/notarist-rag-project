package com.notarist.auth.application.command;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.SessionId;

public record LogoutCommand(
    SessionId sessionId,
    String jti,
    long remainingTokenValiditySeconds,
    CorrelationId correlationId
) {}
