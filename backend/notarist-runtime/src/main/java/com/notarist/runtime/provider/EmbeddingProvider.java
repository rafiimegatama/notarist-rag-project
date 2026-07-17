package com.notarist.runtime.provider;

import java.util.List;

/**
 * Provider-agnostic dense-embedding SPI — one implementation per backend (Ollama bge-m3 today;
 * OpenAI / Gemini / a dedicated bge sidecar later).
 *
 * <p>Concrete providers register as Spring beans; {@code EmbeddingRegistry} (part of the unified
 * {@code RuntimeRegistry}) selects the active one from {@code notarist.runtime.embedding.provider}
 * (env {@code EMBED_PROVIDER}). The existing {@code QueryEmbeddingPort} / {@code EmbeddingPort}
 * adapters delegate through the registry, so search and ingest never bind to a specific backend.
 *
 * <p>Provider and model are separate concerns (Phase 4): {@link #activeModel()} reports the model
 * ({@code EMBED_MODEL}). Every provider MUST return vectors of the platform's required dimension
 * ({@code QdrantVectorPayload.REQUIRED_DIMENSION}) — a mismatch is a hard failure, because the
 * vector index and stored vectors are fixed at that dimension.
 */
public interface EmbeddingProvider extends RuntimeProvider {

    /** Embeds a single text. Convenience over {@link #embedBatch}. */
    float[] embed(String text, String traceId);

    /** Embeds a batch of texts, returning one vector per input in order. */
    List<float[]> embedBatch(List<String> texts, String batchId);
}
