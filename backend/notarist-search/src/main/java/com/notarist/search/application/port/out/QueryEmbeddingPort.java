package com.notarist.search.application.port.out;

/**
 * Output port for query-time dense embedding generation.
 * Implemented by a real bge-m3 adapter in notarist-runtime (EmbeddingRuntimeWorker),
 * wired via Spring component scan from notarist-web — mirrors the VectorSearchPort /
 * LlmPort inversion pattern used elsewhere in this codebase (interface owned by the
 * consuming module, real implementation lives in notarist-runtime).
 *
 * Implementations may throw a RuntimeException on failure (degraded service, timeout,
 * dimension mismatch, etc.) — callers must handle this and degrade gracefully
 * (e.g. fall back to keyword-only retrieval) rather than letting it fail the whole search.
 */
public interface QueryEmbeddingPort {

    /**
     * Encodes a single query string into a dense embedding vector.
     *
     * @param text    normalized query text to embed
     * @param traceId correlation id for logging/metrics/timeout tracing
     * @return a float vector of dimension {@code NotaristConstants.EMBEDDING_DIMENSION}
     */
    float[] embedQuery(String text, String traceId);
}
