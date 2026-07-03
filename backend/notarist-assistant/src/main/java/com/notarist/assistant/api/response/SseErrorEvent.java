package com.notarist.assistant.api.response;

/** SSE event: error — terminal error event. After this, stream closes. */
public record SseErrorEvent(
    String code,
    String message,
    boolean fatal
) {
    public static final String EVENT_TYPE = "error";

    public static SseErrorEvent noSourceFound(String queryText) {
        return new SseErrorEvent(
            "ASSISTANT_NO_SOURCE_FOUND",
            "Tidak ditemukan dokumen relevan untuk pertanyaan ini.",
            true
        );
    }
}
