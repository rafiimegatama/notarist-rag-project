package com.notarist.ingest.infrastructure.metrics;

import com.notarist.ingest.domain.model.PipelineStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Registers and records Micrometer metrics for all ingestion pipeline events. */
@Component
public class IngestionMetricsRegistry {

    private final MeterRegistry registry;

    public IngestionMetricsRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordJobInitiated() {
        Counter.builder("notarist.ingest.job.initiated")
                .description("Total ingestion jobs initiated")
                .register(registry)
                .increment();
    }

    public void recordStageSuccess(PipelineStatus stage, long durationMs) {
        Counter.builder("notarist.ingest.stage.completed")
                .tag("stage", stage.name())
                .description("Successful stage completions")
                .register(registry)
                .increment();

        Timer.builder("notarist.ingest.stage.duration")
                .tag("stage", stage.name())
                .tag("outcome", "success")
                .description("Stage processing duration")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordStageFailure(PipelineStatus stage) {
        Counter.builder("notarist.ingest.stage.failed")
                .tag("stage", stage.name())
                .description("Stage failure count")
                .register(registry)
                .increment();
    }

    public void recordDlqMoved(PipelineStatus stage) {
        Counter.builder("notarist.ingest.dlq.moved")
                .tag("failedStage", stage.name())
                .description("Jobs moved to DLQ")
                .register(registry)
                .increment();
    }

    public void recordUploadConfirmed() {
        Counter.builder("notarist.ingest.upload.confirmed")
                .description("Upload confirmations received")
                .register(registry)
                .increment();
    }

    public void recordDuplicateRejected() {
        Counter.builder("notarist.ingest.duplicate.rejected")
                .description("Duplicate document rejections")
                .register(registry)
                .increment();
    }
}
