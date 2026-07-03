package com.notarist.ingest.domain.model;

import com.notarist.core.domain.valueobject.JobId;

import java.time.Instant;
import java.util.UUID;

/** Immutable DLQ entry — append-only, no updates after creation. */
public record DeadLetterEntry(
        IngestionId ingestionId,
        JobId jobId,
        UUID tenantId,
        PipelineStatus failureStage,
        int retryCount,
        String lastErrorCode,
        String lastErrorHash,
        Instant nextRetryAt,
        String deadLetterReason,
        Instant createdAt
) {
    public DeadLetterEntry {
        if (ingestionId == null) throw new IllegalArgumentException("ingestionId required");
        if (jobId == null) throw new IllegalArgumentException("jobId required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (failureStage == null) throw new IllegalArgumentException("failureStage required");
        if (deadLetterReason == null || deadLetterReason.isBlank())
            throw new IllegalArgumentException("deadLetterReason required");
        if (createdAt == null) createdAt = Instant.now();
    }

    public static DeadLetterEntry create(
            IngestionId ingestionId,
            JobId jobId,
            UUID tenantId,
            PipelineStatus failureStage,
            int retryCount,
            String lastErrorCode,
            String lastErrorHash,
            String deadLetterReason) {
        return new DeadLetterEntry(
                ingestionId, jobId, tenantId, failureStage, retryCount,
                lastErrorCode, lastErrorHash, null, deadLetterReason, Instant.now());
    }
}
