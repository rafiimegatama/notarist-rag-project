package com.notarist.core.util;

/**
 * Platform-wide constants — frozen per STEP 7.5 contract.
 * Changes to chunk size or embedding dimension require re-index strategy.
 */
public final class NotaristConstants {

    private NotaristConstants() {}

    // Chunk size policy — frozen, do not change without re-index strategy
    public static final int AKTA_CHUNK_MAX_TOKENS     = 800;
    public static final int AKTA_CHUNK_MIN_TOKENS     = 400;
    public static final int AKTA_CHUNK_OVERLAP_PCT    = 15;

    public static final int REGULASI_CHUNK_MAX_TOKENS  = 600;
    public static final int REGULASI_CHUNK_MIN_TOKENS  = 300;
    public static final int REGULASI_CHUNK_OVERLAP_PCT = 0;

    public static final int SOP_CHUNK_MAX_TOKENS      = 500;
    public static final int SOP_CHUNK_MIN_TOKENS      = 200;
    public static final int SOP_CHUNK_OVERLAP_PCT     = 10;

    // Retrieval parameters — frozen
    public static final int RRF_K                     = 60;
    public static final int SEMANTIC_TOP_K            = 20;
    public static final int KEYWORD_TOP_K             = 20;
    public static final int RERANKER_TOP_K            = 5;
    public static final int EMBEDDING_DIMENSION       = 1024;

    // Queue
    public static final int QUEUE_MAX_RETRY_ATTEMPTS  = 3;
    public static final int QUEUE_DEFAULT_WORKERS     = 3;

    // API
    public static final String API_VERSION            = "v1";
    public static final String API_BASE_PATH          = "/api/v1";
    public static final int    MAX_PAGE_SIZE          = 100;
    public static final int    DEFAULT_PAGE_SIZE      = 20;

    // Observability headers
    public static final String HEADER_CORRELATION_ID  = "X-Correlation-ID";
    public static final String HEADER_TRACE_ID        = "X-Trace-ID";
    public static final String HEADER_REQUEST_ID      = "X-Request-ID";

    // MinIO buckets
    public static final String BUCKET_RAW             = "notarist-raw";
    public static final String BUCKET_OCR             = "notarist-ocr";
    public static final String BUCKET_PROCESSED       = "notarist-processed";
    public static final String BUCKET_CHUNK           = "notarist-chunk";
    public static final String BUCKET_EXPORT          = "notarist-export";

    // Qdrant
    public static final String QDRANT_COLLECTION      = "notarist_chunks";
}
