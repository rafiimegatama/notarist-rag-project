package com.notarist.assistant.api.response;

import java.util.UUID;

/**
 * Structured SSE event envelope.
 *
 * eventType values:
 *   ANSWER_TOKEN — one streamed sentence fragment from the answer
 *   CITATION     — one citation entry (JSON payload)
 *   CONFIDENCE   — grounding level and score
 *   WARNING      — hallucination or low-grounding warning
 *   FOLLOW_UP    — a suggested follow-up question
 *   DONE         — stream complete; data = traceId
 *   ERROR        — stream error
 */
public record SseEvent(
        String eventType,
        String data,
        String traceId,
        int sequence,
        long timestampMs
) {
    public static SseEvent answerToken(String sentence, UUID traceId, int seq) {
        return new SseEvent("ANSWER_TOKEN", sentence, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent citation(String citationJson, UUID traceId, int seq) {
        return new SseEvent("CITATION", citationJson, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent confidence(String confidenceData, UUID traceId, int seq) {
        return new SseEvent("CONFIDENCE", confidenceData, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent warning(String warningText, UUID traceId, int seq) {
        return new SseEvent("WARNING", warningText, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent followUp(String question, UUID traceId, int seq) {
        return new SseEvent("FOLLOW_UP", question, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent done(String summary, UUID traceId, int seq) {
        return new SseEvent("DONE", summary, traceId.toString(), seq, System.currentTimeMillis());
    }

    public static SseEvent error(String errorMessage, UUID traceId, int seq) {
        return new SseEvent("ERROR", errorMessage, traceId != null ? traceId.toString() : "", seq, System.currentTimeMillis());
    }
}
