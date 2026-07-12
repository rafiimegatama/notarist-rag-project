package com.notarist.auth.api.response;

import java.util.List;
import java.util.UUID;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    int expiresIn,
    UUID userId,
    List<String> roles,
    UUID tenantId,
    UUID sessionId
) {
    public TokenResponse {
        tokenType = "Bearer";
    }
}
