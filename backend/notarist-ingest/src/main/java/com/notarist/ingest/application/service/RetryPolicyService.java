package com.notarist.ingest.application.service;

import com.notarist.ingest.application.port.out.DeadLetterRepository;
import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.application.port.out.IngestQueueRepository;
import com.notarist.ingest.domain.model.DeadLetterEntry;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import com.notarist.ingest.domain.service.PipelineStateMachine;
import com.notarist.ingest.domain.service.RetryPolicy;
import com.notarist.ingest.infrastructure.event.IngestionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Re-enqueues failed jobs that are past their next_retry_at and within max retry limit. */
@Service
public class RetryPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicyService.class);

    private final IngestJobRepository jobRepository;
    private final IngestQueueRepository queueRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final IngestionEventPublisher eventPublisher;
    private final int maxRetries;

    public RetryPolicyService(
            IngestJobRepository jobRepository,
            IngestQueueRepository queueRepository,
            DeadLetterRepository deadLetterRepository,
            IngestionEventPublisher eventPublisher,
            @Value("${notarist.ingest.max-retries:3}") int maxRetries) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.eventPublisher = eventPublisher;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void reEnqueueEligibleJobs() {
        List<IngestionJob> failedJobs = jobRepository.findFailedAndReadyForRetry(maxRetries, 50);

        for (IngestionJob job : failedJobs) {
            if (!RetryPolicy.isReadyForRetry(job.getNextRetryAt())) continue;

            if (!RetryPolicy.shouldRetry(job.getRetryCount(), maxRetries)) {
                escalateToDlq(job);
                continue;
            }

            PipelineStatus retryStage = PipelineStateMachine.retryStageFor(job.getFailureStage());
            job.transitionTo(retryStage);
            jobRepository.save(job);

            queueRepository.enqueue(
                    job.getIngestionId(), job.getJobId(), job.getTenantId(),
                    retryStage, "{}", Instant.now());

            log.info("Re-enqueued ingestionId={} for retry at stage={} attempt={}",
                    job.getIngestionId(), retryStage, job.getRetryCount());
        }
    }

    private void escalateToDlq(IngestionJob job) {
        job.moveToDlq("INGEST_MAX_RETRIES_EXCEEDED");
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
                job.getLastErrorCode(),
                job.getLastErrorHash(),
                "Max retries exceeded after " + job.getRetryCount() + " attempts"
        );
        deadLetterRepository.save(entry);
        eventPublisher.publishDlqMoved(job, "INGEST_MAX_RETRIES_EXCEEDED");
        log.error("Escalated to DLQ ingestionId={} after {} retries",
                job.getIngestionId(), job.getRetryCount());
    }
}
