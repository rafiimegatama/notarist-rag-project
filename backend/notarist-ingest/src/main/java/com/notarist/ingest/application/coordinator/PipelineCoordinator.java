package com.notarist.ingest.application.coordinator;

import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.application.port.out.IngestQueueRepository;
import com.notarist.ingest.application.worker.StageWorker;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import com.notarist.ingest.domain.service.PipelineStateMachine;
import com.notarist.ingest.domain.service.RetryPolicy;
import com.notarist.ingest.infrastructure.event.IngestionEventPublisher;
import com.notarist.ingest.infrastructure.metrics.IngestionMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates pipeline stage execution via registered StageWorker implementations.
 * NOT a mega-service: each StageWorker handles exactly one stage in its own @Transactional.
 * PipelineCoordinator handles orchestration, retry decisions, and audit — not business logic.
 */
@Component
public class PipelineCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PipelineCoordinator.class);

    private final IngestJobRepository jobRepository;
    private final IngestQueueRepository queueRepository;
    private final IngestionEventPublisher eventPublisher;
    private final IngestionMetricsRegistry metrics;
    private final Map<PipelineStatus, StageWorker> workerRegistry;
    private final int maxRetries;

    public PipelineCoordinator(
            IngestJobRepository jobRepository,
            IngestQueueRepository queueRepository,
            IngestionEventPublisher eventPublisher,
            IngestionMetricsRegistry metrics,
            List<StageWorker> workers,
            @Value("${notarist.ingest.max-retries:3}") int maxRetries) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.maxRetries = maxRetries;
        this.workerRegistry = workers.stream()
                .collect(Collectors.toMap(StageWorker::handledStatus, Function.identity()));
    }

    @Transactional
    public void process(IngestQueueRepository.QueueRecord queueRecord) {
        IngestionJob job = jobRepository.findByIngestionId(queueRecord.ingestionId())
                .orElseThrow(() -> new IllegalStateException(
                        "IngestionJob not found for ingestionId: " + queueRecord.ingestionId()));

        PipelineStatus targetStage = queueRecord.targetStage();
        String workerId = queueRecord.queueJobId().toString();

        MDC.put("ingestionId", queueRecord.ingestionId().toString());
        MDC.put("pipelineStage", targetStage.name());

        try {
            StageWorker worker = workerRegistry.get(targetStage);
            if (worker == null) {
                log.error("No worker registered for stage: {}", targetStage);
                queueRepository.moveToDlq(queueRecord.queueJobId(), "NO_WORKER_FOR_STAGE");
                job.moveToDlq("NO_WORKER_FOR_STAGE");
                jobRepository.save(job);
                return;
            }

            long startMs = System.currentTimeMillis();
            StageWorker.WorkerContext ctx = new StageWorker.WorkerContext(
                    queueRecord.ingestionId(), queueRecord.ingestionId().toString(), workerId);

            worker.process(job, ctx);

            long durationMs = System.currentTimeMillis() - startMs;
            PipelineStatus completedStatus = PipelineStateMachine.completedStageFor(targetStage);

            job.transitionTo(completedStatus);
            job.recordStageCompletion(completedStatus, durationMs);
            jobRepository.save(job);
            queueRepository.markCompleted(queueRecord.queueJobId());

            metrics.recordStageSuccess(targetStage, durationMs);
            eventPublisher.publishStageCompleted(job, completedStatus);
            log.info("Stage {} completed for ingestionId={} in {}ms",
                    targetStage, queueRecord.ingestionId(), durationMs);

            enqueueNextStage(job, completedStatus);

        } catch (IngestionStageException ex) {
            handleStageFailure(job, queueRecord, ex);
        } finally {
            MDC.remove("ingestionId");
            MDC.remove("pipelineStage");
        }
    }

    private void enqueueNextStage(IngestionJob job, PipelineStatus completedStatus) {
        PipelineStateMachine.nextPendingStage(completedStatus).ifPresent(nextStage -> {
            queueRepository.enqueue(
                    job.getIngestionId(), job.getJobId(), job.getTenantId(),
                    nextStage, "{}", Instant.now());
            log.info("Enqueued next stage {} for ingestionId={}",
                    nextStage, job.getIngestionId());
        });
    }

    private void handleStageFailure(
            IngestionJob job,
            IngestQueueRepository.QueueRecord queueRecord,
            IngestionStageException ex) {

        int attemptCount = queueRecord.attemptCount() + 1;
        log.warn("Stage {} failed for ingestionId={} attempt={} errorCode={} retryable={}",
                queueRecord.targetStage(), queueRecord.ingestionId(),
                attemptCount, ex.getErrorCode(), ex.isRetryable(), ex);

        job.recordStageFailure(queueRecord.targetStage(), ex.getErrorCode(),
                System.currentTimeMillis());

        metrics.recordStageFailure(queueRecord.targetStage());

        boolean canRetry = ex.isRetryable()
                && RetryPolicy.shouldRetry(attemptCount, maxRetries);

        if (canRetry) {
            Instant nextRetryAt = RetryPolicy.computeNextRetryAt(attemptCount);
            job.scheduleRetry(nextRetryAt);
            jobRepository.save(job);
            queueRepository.markFailed(queueRecord.queueJobId(), ex.getErrorCode(),
                    attemptCount, nextRetryAt);
            log.info("Scheduled retry {} for ingestionId={} at {}",
                    attemptCount, queueRecord.ingestionId(), nextRetryAt);
        } else {
            job.moveToDlq(ex.getErrorCode() + ": " + ex.getMessage());
            jobRepository.save(job);
            queueRepository.moveToDlq(queueRecord.queueJobId(), ex.getErrorCode());
            eventPublisher.publishDlqMoved(job, ex.getErrorCode());
            metrics.recordDlqMoved(queueRecord.targetStage());
            log.error("Moved to DLQ ingestionId={} reason={}", queueRecord.ingestionId(), ex.getErrorCode());
        }
    }
}
