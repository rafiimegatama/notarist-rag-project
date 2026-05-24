package com.notarist.ingest.application.service;

import com.notarist.ingest.application.port.out.DeadLetterRepository;
import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.domain.model.DeadLetterEntry;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import com.notarist.ingest.infrastructure.event.IngestionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Handles explicit DLQ escalation — stores entry, transitions job, publishes audit event. */
@Service
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final IngestJobRepository jobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final IngestionEventPublisher eventPublisher;

    public DeadLetterHandler(
            IngestJobRepository jobRepository,
            DeadLetterRepository deadLetterRepository,
            IngestionEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handleDlq(IngestionJob job, String errorCode, String reason) {
        job.moveToDlq(reason);
        jobRepository.save(job);

        PipelineStatus failedStage = job.getFailureStage() != null
                ? PipelineStatus.valueOf(job.getFailureStage())
                : PipelineStatus.FAILED;

        DeadLetterEntry entry = DeadLetterEntry.create(
                job.getIngestionId(),
                job.getJobId(),
                job.getTenantId(),
                failedStage,
                job.getRetryCount(),
                errorCode,
                job.getLastErrorHash(),
                reason
        );
        deadLetterRepository.save(entry);
        eventPublisher.publishDlqMoved(job, errorCode);

        log.error("Job moved to DLQ: ingestionId={} stage={} reason={}",
                job.getIngestionId(), failedStage, reason);
    }

    @Transactional(readOnly = true)
    public java.util.List<DeadLetterEntry> getDeadLetters(UUID tenantId, int limit) {
        return deadLetterRepository.findByTenantId(tenantId, limit);
    }
}
