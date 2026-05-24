package com.notarist.core.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(
    String requestId,
    Instant timestamp,
    String apiVersion,
    Long processingMs
) {
    public static ApiMeta of(String requestId) {
        return new ApiMeta(requestId, Instant.now(), "v1", null);
    }

    public static ApiMeta of(String requestId, long processingMs) {
        return new ApiMeta(requestId, Instant.now(), "v1", processingMs);
    }
}
