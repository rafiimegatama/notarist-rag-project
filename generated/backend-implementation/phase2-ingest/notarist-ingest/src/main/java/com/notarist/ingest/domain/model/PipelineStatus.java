package com.notarist.ingest.domain.model;

/**
 * Authoritative pipeline state enum for Phase 2 ingest implementation.
 * Replaces PipelineStage skeleton for all new implementation code.
 */
public enum PipelineStatus {

    UPLOADED,
    OCR_PENDING,
    OCR_COMPLETED,
    NER_PENDING,
    NER_COMPLETED,
    CHUNK_PENDING,
    CHUNK_COMPLETED,
    EMBED_PENDING,
    INDEX_PENDING,
    COMPLETED,
    FAILED,
    DLQ;

    public boolean isTerminal() {
        return this == COMPLETED || this == DLQ;
    }

    public boolean isFailure() {
        return this == FAILED || this == DLQ;
    }

    public boolean isPending() {
        return name().endsWith("_PENDING");
    }
}
