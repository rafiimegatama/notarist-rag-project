package com.notarist.document.domain.model;

/** Life-cycle status of a legal document in the NOTARIST platform. */
public enum DocumentStatus {
    UPLOADED,
    OCR_QUEUE,
    OCR_PROCESSING,
    NER_QUEUE,
    NER_PROCESSING,
    CHUNKING_QUEUE,
    CHUNKING_PROCESSING,
    EMBEDDING_QUEUE,
    EMBEDDING_PROCESSING,
    INDEXING_QUEUE,
    INDEXING_PROCESSING,
    INDEXED,
    FAILED,
    DLQ
}
