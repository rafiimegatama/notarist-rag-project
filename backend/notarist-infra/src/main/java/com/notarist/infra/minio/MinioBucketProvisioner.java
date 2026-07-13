package com.notarist.infra.minio;

import com.notarist.infra.resilience.DegradedModeRegistry;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the MinIO buckets the platform needs, if they do not already exist (F16).
 *
 * <p>Nothing else in the system ever created them: MinioDocumentStorageAdapter goes straight to
 * getPresignedObjectUrl/statObject/copyObject against notarist-raw, so on a fresh MinIO volume
 * the entire upload flow failed with NoSuchBucket.
 *
 * <p><b>Why in code rather than a docker-compose `mc mb` bootstrap:</b> compose only covers the
 * local dev stack. Staging/production point at an existing MinIO (or S3-compatible) cluster that
 * this compose file never launches, so a compose-side step would leave exactly the environments
 * that matter unprovisioned. A create-if-absent check at startup is idempotent, runs in every
 * deployment topology, and costs one bucketExists call per bucket per boot.
 *
 * <p><b>Failure policy:</b> loud, not fatal. A wrong credential or an unreachable MinIO is logged
 * at ERROR and marks the MINIO service degraded, so /actuator/health reports DOWN at startup
 * instead of the first upload failing mysteriously later. The application still boots — MinIO is
 * one of several external dependencies and the rest of the platform (auth, search, assistant)
 * must not be taken down with it.
 *
 * <p>Set {@code notarist.storage.minio.auto-create-buckets: false} where buckets are provisioned
 * by an operator/terraform and the app credential is not allowed to create buckets.
 */
@Component
@Order(0)
public class MinioBucketProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketProvisioner.class);

    private final MinioClient          minioClient;
    private final MinioProperties      props;
    private final DegradedModeRegistry degradedMode;

    public MinioBucketProvisioner(MinioClient minioClient,
                                  MinioProperties props,
                                  DegradedModeRegistry degradedMode) {
        this.minioClient  = minioClient;
        this.props        = props;
        this.degradedMode = degradedMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(props.autoCreateBuckets())) {
            log.info("MinIO bucket auto-creation disabled (notarist.storage.minio.auto-create-buckets=false); "
                    + "buckets {} must already exist", props.allBuckets());
            return;
        }
        provisionBuckets();
    }

    /** Idempotent: existing buckets are left untouched. Package-private for direct invocation in tests. */
    void provisionBuckets() {
        List<String> created = new ArrayList<>();
        try {
            for (String bucket : props.allBuckets()) {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (exists) {
                    log.debug("MinIO bucket present: {}", bucket);
                    continue;
                }
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                created.add(bucket);
                log.info("MinIO bucket created: {}", bucket);
            }

            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);
            log.info("MinIO bucket provisioning complete endpoint={} buckets={} created={}",
                    props.endpoint(), props.allBuckets(), created.isEmpty() ? "none" : created);

        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
            log.error("MinIO bucket provisioning FAILED endpoint={} buckets={}: {} — "
                            + "document upload/OCR/chunk storage will not work until this is resolved. "
                            + "Check MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY (the secret key must "
                            + "match the server's MINIO_ROOT_PASSWORD).",
                    props.endpoint(), props.allBuckets(), e.getMessage(), e);
        }
    }
}
