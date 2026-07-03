package com.notarist.ingest.application.worker;

import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;

/** Contract for each pipeline stage worker. Each implementation handles one PENDING stage. */
public interface StageWorker {

    /** The PENDING status this worker handles. */
    PipelineStatus handledStatus();

    /**
     * Performs the stage work.
     * Must be idempotent — safe to retry on failure.
     * Throws IngestionStageException on failure — caller determines retry vs DLQ.
     */
    void process(IngestionJob job, WorkerContext context) throws IngestionStageException;

    record WorkerContext(
            IngestionId ingestionId,
            String correlationId,
            String workerId
    ) {}
}
