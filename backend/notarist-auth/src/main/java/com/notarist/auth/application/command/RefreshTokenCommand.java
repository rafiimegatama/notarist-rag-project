package com.notarist.auth.application.command;

import com.notarist.core.domain.valueobject.CorrelationId;

public record RefreshTokenCommand(
    String refreshToken,
    String ipAddress,
    CorrelationId correlationId
) {}
