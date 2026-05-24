package com.notarist.core.domain.vector;

/**
 * Immutable embedding dimension contract for the notarist vector pipeline.
 *
 * REQUIRED_DIMENSION = 1024: enforced by both the embedding service (bge-m3)
 * and the Qdrant collection schema. Changing this requires full re-indexing.
 *
 * Canonical location: notarist-core.
 * Used by notarist-runtime (EmbeddingRuntimeWorker) and notarist-infra (QdrantIndexAdapter,
 * QdrantSearchAdapter, QdrantVectorPayload) to prevent dimension drift.
 */
public final class EmbeddingContract {

    public static final int    REQUIRED_DIMENSION = 1024;
    public static final String EMBEDDING_MODEL    = "bge-m3";
    public static final String EMBEDDING_VERSION  = "1.0.0";

    private EmbeddingContract() {}
}
