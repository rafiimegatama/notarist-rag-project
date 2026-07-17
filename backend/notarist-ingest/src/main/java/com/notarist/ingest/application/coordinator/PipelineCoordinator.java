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

            if (completedStatus == PipelineStatus.COMPLETED) {
                // Terminal success: the document is fully ingested and searchable. Announce it so the
                // Case context can advance. Published inside this transaction; consumed AFTER_COMMIT.
                eventPublisher.publishDocumentIngestionTerminal(job, true);
            }

            enqueueNextStage(job, completedStatus);

        } catch (IngestionStageException ex) {
            handleStageFailure(job, queueRecord, ex);
        } finally {
            MDC.remove("ingestionId");
            MDC.remove("pipelineStage");
        }
    }

    /**
     * Advance the job to the next PENDING stage and enqueue the work for it. Both halves, together:
     * the queue says what to run next, the job status says where the job IS, and the state machine
     * reads the job status.
     *
     * <p>This used to enqueue only, and that stalled every pipeline at the first stage boundary.
     * The job stayed on OCR_COMPLETED while the queue dispatched NER_PENDING; the NER worker then
     * did its real work and {@code process()} asked for OCR_COMPLETED → NER_COMPLETED, which the
     * state machine rejects (OCR_COMPLETED may only go to NER_PENDING). The result was
     * INGEST_INVALID_TRANSITION, non-retryable, straight to the DLQ — after OCR and NER had both
     * already succeeded and paid for their sidecar calls. UploadOrchestrationService.confirmUpload
     * always did both halves for the first hop, which is why OCR alone appeared to work.
     *
     * <p>The self-mapping guard is required, not defensive: EMBED_PENDING completes AS
     * INDEX_PENDING (there is no EMBED_COMPLETED — see PipelineStateMachine.nextPendingStage), so
     * nextPendingStage(INDEX_PENDING) returns INDEX_PENDING and the job is already there.
     * INDEX_PENDING → INDEX_PENDING is not a legal transition, so re-asserting it would move the
     * stall from the NER boundary to the INDEX boundary.
     */
    private void enqueueNextStage(IngestionJob job, PipelineStatus completedStatus) {
        PipelineStateMachine.nextPendingStage(completedStatus).ifPresent(nextStage -> {
            if (job.getPipelineStatus() != nextStage) {
                job.transitionTo(nextStage);
                jobRepository.save(job);
            }
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

        job.recordStageFailure(queueRecord.targetStage(), ex.getErrorCode(),
                System.currentTimeMillis());

        metrics.recordStageFailure(queueRecord.targetStage());

        // Single source of truth for retry counting is the job aggregate's retryCount.
        // (Previously the decision used the queue row's attempt_count, which diverged from
        // job.retryCount because RetryPolicyService re-enqueues with a reset attempt_count — F15.)
        boolean canRetry = ex.isRetryable()
                && RetryPolicy.shouldRetry(job.getRetryCount(), maxRetries);

        log.warn("Stage {} failed for ingestionId={} retryCount={} errorCode={} retryable={}",
                queueRecord.targetStage(), queueRecord.ingestionId(),
                job.getRetryCount(), ex.getErrorCode(), ex.isRetryable(), ex);

        if (canRetry) {
            Instant nextRetryAt = RetryPolicy.computeNextRetryAt(job.getRetryCount() + 1);
            job.scheduleRetry(nextRetryAt);          // retryCount -> retryCount + 1
            jobRepository.save(job);
            // Mark the claimed row terminally FAILED and mirror the authoritative counter.
            // RetryPolicyService alone re-enqueues (transitioning FAILED -> retry stage), so the
            // retry is driven once, by one path, counted once.
            queueRepository.markFailed(queueRecord.queueJobId(), ex.getErrorCode(),
                    job.getRetryCount(), nextRetryAt);
            log.info("Scheduled retry {} for ingestionId={} at {}",
                    job.getRetryCount(), queueRecord.ingestionId(), nextRetryAt);
        } else {
            job.moveToDlq(ex.getErrorCode() + ": " + ex.getMessage());
            jobRepository.save(job);
            queueRepository.moveToDlq(queueRecord.queueJobId(), ex.getErrorCode());
            eventPublisher.publishDlqMoved(job, ex.getErrorCode());
            // Terminal failure: tell the Case context this document will not finish, so a case
            // waiting on it can move to OCR_FAILED instead of hanging in OCR_RUNNING forever.
            eventPublisher.publishDocumentIngestionTerminal(job, false);
            metrics.recordDlqMoved(queueRecord.targetStage());
            log.error("Moved to DLQ ingestionId={} reason={}", queueRecord.ingestionId(), ex.getErrorCode());
        }
    }
}
