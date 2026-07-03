package com.notarist.assistant.application.port.out;

import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;

import java.util.function.Consumer;

/**
 * Output port for LLM inference.
 * Implemented by OllamaAdapter (stub in Phase 4).
 * Real Ollama HTTP integration deferred to Phase 5.
 */
public interface LlmPort {

    /** Synchronous invocation — blocks until full response is received. */
    LlmResponse invoke(LlmRequest request);

    /** Streaming invocation — calls chunkConsumer for each token chunk. */
    void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer);

    /** Returns false in Phase 4 stub — no real Ollama connection. */
    boolean isAvailable();
}
