package com.notarist.infra.minio;

import com.notarist.infra.resilience.DegradedModeRegistry;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for MinIO.
 * Checks bucket existence as a lightweight liveness probe.
 * Reports DEGRADED status from DegradedModeRegistry when persistent failures exist.
 */
@Component
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient          minioClient;
    private final MinioProperties      props;
    private final DegradedModeRegistry degradedMode;

    public MinioHealthIndicator(MinioClient minioClient, MinioProperties props, DegradedModeRegistry degradedMode) {
        this.minioClient  = minioClient;
        this.props        = props;
        this.degradedMode = degradedMode;
    }

    @Override
    public Health health() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.defaultBucket()).build());

            if (bucketExists) {
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);
                return Health.up()
                        .withDetail("endpoint", props.endpoint())
                        .withDetail("bucket", props.defaultBucket())
                        .withDetail("degraded", false)
                        .build();
            } else {
                return Health.down()
                        .withDetail("reason", "Bucket not found: " + props.defaultBucket())
                        .build();
            }
        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
            return Health.down(e)
                    .withDetail("endpoint", props.endpoint())
                    .withDetail("degraded", true)
                    .build();
        }
    }
}
