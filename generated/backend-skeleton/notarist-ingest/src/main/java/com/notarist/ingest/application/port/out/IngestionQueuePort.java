package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStage;

import java.util.Optional;

/** Port for PostgreSQL SKIP LOCKED queue operations. */
public interface IngestionQueuePort {
    void enqueue(IngestionJob job, PipelineStage stage, String payloadJson);
    Optional<QueuedItem> dequeueNext(PipelineStage stage, String workerId);
    void markCompleted(JobId jobId, PipelineStage stage);
    void markFailed(JobId jobId, PipelineStage stage, String reason, int attemptCount);
    void moveToDeadLetter(JobId jobId, PipelineStage stage, String lastError);

    record QueuedItem(String queueId, JobId jobId, String payloadJson, int attemptCount) {}
}
