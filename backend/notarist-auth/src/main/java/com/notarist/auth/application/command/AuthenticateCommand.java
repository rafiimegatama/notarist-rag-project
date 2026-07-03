package com.notarist.auth.application.command;

import com.notarist.core.domain.valueobject.CorrelationId;

public record AuthenticateCommand(
    String username,
    String password,
    String ipAddress,
    String userAgent,
    CorrelationId correlationId
) {}
