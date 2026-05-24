package com.notarist.assistant.api.response;

import java.util.List;
import java.util.UUID;

/** SSE event: complete — terminal event when LLM response finishes. */
public record SseCompleteEvent(
    UUID sessionId,
    int totalTokens,
    int citationCount,
    float groundingScore,
    long durationMs,
    long ttftMs,
    boolean hallucinationFlagRaised,
    List<String> warnings
) {
    public static final String EVENT_TYPE = "complete";

    public SseCompleteEvent {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
}
