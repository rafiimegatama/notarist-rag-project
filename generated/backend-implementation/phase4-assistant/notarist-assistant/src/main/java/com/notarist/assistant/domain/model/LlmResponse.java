package com.notarist.assistant.domain.model;

/**
 * LLM response contract. isStub=true flags Phase 4 stub responses.
 */
public record LlmResponse(
        String content,
        String model,
        int promptTokens,
        int completionTokens,
        long durationMs,
        boolean truncated,
        boolean isStub
) {
    public static LlmResponse stub(String content) {
        return new LlmResponse(content, "stub", 0, 0, 0L, false, true);
    }

    public static LlmResponse unavailable() {
        return stub("Saya tidak menemukan dasar dokumen yang cukup untuk memastikan jawaban ini. " +
                    "Layanan LLM belum tersedia pada fase ini.");
    }
}
