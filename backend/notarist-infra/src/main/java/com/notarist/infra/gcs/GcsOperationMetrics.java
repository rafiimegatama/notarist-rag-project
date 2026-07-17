package com.notarist.infra.gcs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GcsOperationMetrics {

    private static final String PREFIX = "notarist.gcs";

    private final MeterRegistry meterRegistry;
    private final Counter       signedUrlsGenerated;
    private final Timer         downloadTimer;
    private final Timer         moveTimer;
    private final Timer         statTimer;

    public GcsOperationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.signedUrlsGenerated = Counter.builder(PREFIX + ".signed_url.generated")
                .description("V4 signed upload URLs generated")
                .register(meterRegistry);

        this.downloadTimer = Timer.builder(PREFIX + ".download.duration")
                .description("GCS object download duration")
                .register(meterRegistry);

        this.moveTimer = Timer.builder(PREFIX + ".move.duration")
                .description("GCS object move (copy+delete) duration")
                .register(meterRegistry);

        this.statTimer = Timer.builder(PREFIX + ".stat.duration")
                .description("GCS object existence check duration")
                .register(meterRegistry);
    }

    public void recordSignedUrlGenerated(long durationMs) {
        signedUrlsGenerated.increment();
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
