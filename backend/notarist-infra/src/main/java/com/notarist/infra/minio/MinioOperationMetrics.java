package com.notarist.infra.minio;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MinioOperationMetrics {

    private static final String PREFIX = "notarist.minio";

    private final MeterRegistry meterRegistry;
    private final Counter       presignedUrlsGenerated;
    private final Counter       operationFailures;
    private final Timer         downloadTimer;
    private final Timer         moveTimer;
    private final Timer         statTimer;

    public MinioOperationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.presignedUrlsGenerated = Counter.builder(PREFIX + ".presigned_url.generated")
                .description("Presigned upload URLs generated")
                .register(meterRegistry);

        this.operationFailures = Counter.builder(PREFIX + ".operation.failures")
                .description("MinIO operation failures")
                .register(meterRegistry);

        this.downloadTimer = Timer.builder(PREFIX + ".download.duration")
                .description("MinIO object download duration")
                .register(meterRegistry);

        this.moveTimer = Timer.builder(PREFIX + ".move.duration")
                .description("MinIO object move (copy+delete) duration")
                .register(meterRegistry);

        this.statTimer = Timer.builder(PREFIX + ".stat.duration")
                .description("MinIO object existence check duration")
                .register(meterRegistry);
    }

    public void recordPresignedUrlGenerated(long durationMs) {
        presignedUrlsGenerated.increment();
    }

    public void recordDownload(long durationMs) {
        downloadTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordMoveObject(long durationMs) {
        moveTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordStatObject(long durationMs) {
        statTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordOperationFailed(String operation) {
        Counter.builder(PREFIX + ".operation.failures")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }
}
