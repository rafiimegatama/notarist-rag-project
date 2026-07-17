package com.notarist.runtime.provider;

/**
 * Common contract shared by every AI runtime provider (LLM, embedding, reranker). Lets the unified
 * {@code RuntimeRegistry} and health endpoint treat any provider uniformly — id, model, capabilities
 * and health — while the capability-specific work (invoke/stream, embed, rerank) lives on the
 * sub-interfaces.
 *
 * <p>This is the AI-runtime analogue of the OCR module's {@code OcrProvider}; keeping the same shape
 * means one mental model for the whole {@code notarist-runtime} module.
 */
public interface RuntimeProvider {

    /** Stable lowercase id used for config selection, e.g. {@code "ollama"}. Unique across a kind. */
    String id();

    /** Human-readable name for logs and the health endpoint. */
    default String displayName() {
        return id();
    }

    /** The model this provider is currently configured to serve (provider ≠ model). */
    String activeModel();

    /** What this provider/model can do. The runtime reads this instead of assuming. */
    ProviderCapabilities capabilities();

    /** True when this provider is reachable and not degraded. */
    boolean isAvailable();

    /** Cheap, non-throwing probe for the health endpoint and startup checks. */
    RuntimeProviderHealth health();
}
