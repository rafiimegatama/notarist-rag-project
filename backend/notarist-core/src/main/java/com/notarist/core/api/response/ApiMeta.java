package com.notarist.core.api.response;

import java.time.Instant;

/** Response envelope metadata — correlation-id and timestamp. */
public record ApiMeta(
        String correlationId,
        String timestamp
) {
    public static ApiMeta of(String correlationId) {
        return new ApiMeta(correlationId, Instant.now().toString());
    }
}
