package com.notarist.ingest.domain.model;

public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    DLQ
}
