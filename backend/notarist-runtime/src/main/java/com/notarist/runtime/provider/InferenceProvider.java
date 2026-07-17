package com.notarist.runtime.provider;

import com.notarist.assistant.domain.model.LlmRequest;
import com.notarist.assistant.domain.model.LlmResponse;
import com.notarist.assistant.domain.model.LlmStreamChunk;

import java.util.function.Consumer;

/**
 * Provider-agnostic LLM inference SPI — one implementation per backend (Ollama today; vLLM /
 * OpenAI / Gemini / Anthropic / OpenRouter / TensorRT-LLM later).
 *
 * <p>Concrete providers register as Spring beans; {@code LlmRegistry} (part of the unified
 * {@code RuntimeRegistry}) selects the active one from {@code notarist.runtime.llm.provider}
 * (env {@code LLM_PROVIDER}). The {@code RegistryLlmPort} router is the single {@code LlmPort} bean
 * the application sees, so no business-logic code depends on any specific provider.
 *
 * <p>Provider and model are separate concerns (Phase 4): {@link #activeModel()} reports the model
 * this provider is configured to serve ({@code LLM_MODEL}), independent of the provider id.
 *
 * <p>Adding a provider is Phase 9's whole promise: implement this interface, annotate
 * {@code @Component}, give it a unique {@link #id()}. No registry, router, or business-logic edit.
 */
public interface InferenceProvider extends RuntimeProvider {

    /** Synchronous invocation — blocks until the full response is received. */
    LlmResponse invoke(LlmRequest request);

    /** Streaming invocation — calls {@code chunkConsumer} for each token chunk. */
    void stream(LlmRequest request, Consumer<LlmStreamChunk> chunkConsumer);

    /** Opens a cancellable scope for {@code traceId} before {@link #stream} is queued. */
    default void openStream(String traceId) { }

    /** Cancels an in-flight or still-queued streaming inference for {@code traceId}. */
    default boolean cancelStream(String traceId) { return false; }

    /** Releases the cancellable scope for {@code traceId}. Idempotent. */
    default void closeStream(String traceId) { }
}
