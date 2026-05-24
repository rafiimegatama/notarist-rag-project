package com.notarist.ingest.domain.model;

/** Ingestion pipeline stages — sequential, one-directional. */
public enum PipelineStage {
    UPLOAD_CONFIRMED(0),
    OCR_QUEUE(1),
    OCR_PROCESSING(1),
    NER_QUEUE(2),
    NER_PROCESSING(2),
    CHUNKING_QUEUE(3),
    CHUNKING_PROCESSING(3),
    EMBEDDING_QUEUE(4),
    EMBEDDING_PROCESSING(4),
    INDEXING_QUEUE(5),
    INDEXING_PROCESSING(5),
    COMPLETED(6);

    private final int order;

    PipelineStage(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    public boolean isBefore(PipelineStage other) {
        return this.order < other.order;
    }
}
