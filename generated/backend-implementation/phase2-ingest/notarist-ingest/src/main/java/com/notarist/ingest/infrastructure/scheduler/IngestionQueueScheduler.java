package com.notarist.ingest.infrastructure.scheduler;

import com.notarist.ingest.application.coordinator.PipelineCoordinator;
import com.notarist.ingest.application.port.out.IngestQueueRepository;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls ingestion_queue for each PENDING stage and dispatches to PipelineCoordinator.
 * Runs per-stage polls on fixed delay to avoid head-of-line blocking across stages.
 * Each poll dequeues up to concurrencyPerStage records (SKIP LOCKED).
 */
@Component
public class IngestionQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionQueueScheduler.class);

    private static final List<PipelineStatus> PENDING_STAGES = List.of(
            PipelineStatus.OCR_PENDING,
            PipelineStatus.NER_PENDING,
            PipelineStatus.CHUNK_PENDING,
            PipelineStatus.EMBED_PENDING,
            PipelineStatus.INDEX_PENDING
    );

    private final IngestQueueRepository queueRepository;
    private final PipelineCoordinator coordinator;
    private final int concurrencyPerStage;
    private final String schedulerId;

    public IngestionQueueScheduler(
            IngestQueueRepository queueRepository,
            PipelineCoordinator coordinator,
            @Value("${notarist.ingest.scheduler.concurrency-per-stage:3}") int concurrencyPerStage,
            @Value("${notarist.ingest.scheduler.worker-id:scheduler-default}") String schedulerId) {
        this.queueRepository = queueRepository;
        this.coordinator = coordinator;
        this.concurrencyPerStage = concurrencyPerStage;
        this.schedulerId = schedulerId;
    }

    @Scheduled(fixedDelayString = "${notarist.ingest.scheduler.poll-interval-ms:5000}")
    public void pollAndDispatch() {
        for (PipelineStatus stage : PENDING_STAGES) {
            try {
                List<IngestQueueRepository.QueueRecord> records =
                        queueRepository.dequeueForProcessing(stage, schedulerId, concurrencyPerStage);

                if (!records.isEmpty()) {
                    log.info("Scheduler dispatching {} record(s) for stage={}", records.size(), stage);
                }

                for (IngestQueueRepository.QueueRecord record : records) {
                    try {
                        coordinator.process(record);
                    } catch (Exception ex) {
                        log.error("Unhandled error processing queueJobId={} stage={}: {}",
                                record.queueJobId(), stage, ex.getMessage(), ex);
                    }
                }
            } catch (Exception ex) {
                log.error("Scheduler poll failed for stage={}: {}", stage, ex.getMessage(), ex);
            }
        }
    }
}
