package com.notarist.assistant.domain.model;

/**
 * Contract for an LLM invocation.
 * model: Ollama model tag (e.g., "llama3.2:3b-instruct-q8_0").
 * temperature: 0.0 for deterministic legal answers.
 * stream: false for synchronous; true for SSE streaming path.
 */
public record LlmRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens,
        boolean stream,
        String traceId
) {
    private static final String DEFAULT_MODEL = "llama3.2:3b-instruct-q8_0";

    public static LlmRequest strict(String systemPrompt, String userPrompt, String traceId) {
        return new LlmRequest(DEFAULT_MODEL, systemPrompt, userPrompt, 0.0, 2048, false, traceId);
    }

    public static LlmRequest streaming(String systemPrompt, String userPrompt, String traceId) {
        return new LlmRequest(DEFAULT_MODEL, systemPrompt, userPrompt, 0.0, 2048, true, traceId);
    }
}
