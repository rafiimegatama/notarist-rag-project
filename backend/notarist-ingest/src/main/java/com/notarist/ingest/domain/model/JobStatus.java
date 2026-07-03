package com.notarist.ingest.domain.model;

/** Overall job lifecycle status — coarser-grained than PipelineStatus. */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    DLQ;

    public boolean isTerminal() {
        return this == COMPLETED || this == DLQ;
    }
}
