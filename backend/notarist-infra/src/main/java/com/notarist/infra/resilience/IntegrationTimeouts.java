package com.notarist.infra.resilience;

/**
 * Canonical timeout constants for all external integrations.
 *
 * These values represent the MAXIMUM acceptable latency per operation.
 * Changing them requires corresponding updates to SLA documentation.
 *
 * Rationale:
 *   MinIO write 120s  — large PDF uploads (up to ~200MB)
 *   Qdrant search 5s  — interactive search must feel fast; fallback to BM25 beyond this
 *   Qdrant upsert 10s — batch index writes, less latency-sensitive than reads
 *   Postgres query 30s — complex BM25 queries on large chunk_index tables
 */
public final class IntegrationTimeouts {

    private IntegrationTimeouts() {}

    // MinIO
    public static final int MINIO_CONNECT_MS = 5_000;
    public static final int MINIO_READ_MS    = 30_000;
    public static final int MINIO_WRITE_MS   = 120_000;

    // Qdrant
    public static final int QDRANT_CONNECT_MS = 3_000;
    public static final int QDRANT_SEARCH_MS  = 5_000;
    public static final int QDRANT_UPSERT_MS  = 10_000;

    // PostgreSQL
    public static final int POSTGRES_CONNECT_MS = 5_000;
    public static final int POSTGRES_QUERY_S    = 30;    // JdbcTemplate.setQueryTimeout uses seconds

    // Ollama (Phase 5B)
    public static final int OLLAMA_CONNECT_MS       = 3_000;
    public static final int OLLAMA_INFERENCE_MS     = 60_000;
    public static final int OLLAMA_STREAM_FIRST_MS  = 10_000;  // first token deadline

    // OCR / Embedding (Phase 5B)
    public static final int OCR_PAGE_TIMEOUT_MS       = 30_000;
    public static final int EMBEDDING_BATCH_TIMEOUT_MS = 15_000;
}
