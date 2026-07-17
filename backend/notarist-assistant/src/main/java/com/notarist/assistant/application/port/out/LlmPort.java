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

    /** True when the LLM runtime is reachable and not degraded. */
    boolean isAvailable();

    /**
     * Opens a cancellable scope for traceId BEFORE {@link #stream} is called.
     *
     * <p>Without this, a cancel that arrives while the request is still queued behind the
     * single-threaded inference executor would be lost, and the inference would then run to
     * completion for a client that is already gone. Must be paired with {@link #closeStream}.
     */
    default void openStream(String traceId) { }

    /**
     * Cancels the in-flight (or still-queued) streaming inference for traceId.
     * Idempotent; returns false if nothing was cancellable.
     */
    default boolean cancelStream(String traceId) { return false; }

    /** Releases the cancellable scope for traceId. Idempotent. */
    default void closeStream(String traceId) { }
}
