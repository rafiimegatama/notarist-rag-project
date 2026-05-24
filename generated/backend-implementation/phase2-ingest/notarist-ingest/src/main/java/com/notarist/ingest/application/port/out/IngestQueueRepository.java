package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.PipelineStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Port for PostgreSQL SKIP LOCKED ingestion queue. Uses PipelineStatus (not PipelineStage). */
public interface IngestQueueRepository {

    void enqueue(IngestionId ingestionId, JobId jobId, UUID tenantId,
                 PipelineStatus targetStage, String payloadJson, Instant scheduledAt);

    List<QueueRecord> dequeueForProcessing(PipelineStatus targetStage, String workerId, int limit);

    void markCompleted(UUID queueJobId);

    void markFailed(UUID queueJobId, String errorCode, int attemptCount, Instant nextRetryAt);

    void moveToDlq(UUID queueJobId, String reason);

    long countPending(UUID tenantId);

    record QueueRecord(
            UUID queueJobId,
            IngestionId ingestionId,
            JobId jobId,
            UUID tenantId,
            PipelineStatus targetStage,
            String payloadJson,
            int attemptCount,
            Instant scheduledAt
    ) {}
}
