package com.notarist.assistant.application.port.out;

import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;

import java.util.function.Consumer;

/**
 * Output port for LLM inference.
 * Implemented by OllamaRuntimeAdapter in notarist-runtime
 * (real Ollama HTTP + NDJSON streaming).
 */
public interface LlmPort {

    /** Synchronous invocation — blocks until full response is received. */
    LlmResponse invoke(LlmRequest request);

    /** Streaming invocation — calls chunkConsumer for each token chunk. */
    void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer);

    /** Returns false in Phase 4 stub — no real Ollama connection. */
    boolean isAvailable();
}
