package com.notarist.assistant.api.response;

import java.util.UUID;

/** SSE event: token — single LLM output token. */
public record SseTokenEvent(
    String token,
    int index,
    UUID sessionId
) {
    public static final String EVENT_TYPE = "token";
}
