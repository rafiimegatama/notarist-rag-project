package com.notarist.assistant.api.response;

/** SSE event: warning — non-fatal, e.g., hallucination detected or low grounding. */
public record SseWarningEvent(
    String warningCode,
    String message,
    String severity
) {
    public static final String EVENT_TYPE = "warning";

    public enum WarningCode {
        HALLUCINATION_DETECTED,
        LOW_GROUNDING,
        CITATION_UNVERIFIED,
        PARTIAL_SOURCE
    }
}
