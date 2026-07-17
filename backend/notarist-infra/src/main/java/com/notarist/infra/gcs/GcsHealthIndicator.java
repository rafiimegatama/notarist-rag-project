package com.notarist.infra.gcs;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.notarist.infra.resilience.DegradedModeRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for GCS.
 * Checks that the backing bucket is reachable as a lightweight liveness probe.
 * Reports DOWN and marks GCS degraded on failure.
 */
@Component
public class GcsHealthIndicator implements HealthIndicator {

    private final Storage              storage;
    private final GcsProperties        props;
    private final DegradedModeRegistry degradedMode;

    public GcsHealthIndicator(@Lazy Storage storage, GcsProperties props, DegradedModeRegistry degradedMode) {
        this.storage      = storage;
        this.props        = props;
        this.degradedMode = degradedMode;
    }

    @Override
    public Health health() {
        try {
            Bucket bucket = storage.get(props.defaultBucket());
            if (bucket != null) {
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
                return Health.up()
                        .withDetail("bucket", props.defaultBucket())
                        .withDetail("location", bucket.getLocation())
                        .withDetail("degraded", false)
                        .build();
            }
            return Health.down()
                    .withDetail("reason", "Bucket not found: " + props.defaultBucket())
                    .build();
        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
            return Health.down(e)
                    .withDetail("bucket", props.defaultBucket())
                    .withDetail("degraded", true)
                    .build();
        }
    }
}
