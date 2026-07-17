package com.notarist.infra.gcs;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.notarist.infra.resilience.DegradedModeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Verifies the single backing bucket exists at startup, optionally creating it (F16 parity with
 * the former MinIO provisioner).
 *
 * <p><b>Difference from MinIO:</b> in production the bucket is normally provisioned by Terraform /
 * {@code gcloud} and the Cloud Run runtime service account is granted only object-level access
 * (roles/storage.objectAdmin), not {@code storage.buckets.create}. So the default posture here is
 * <b>verify, warn if missing</b>. Set {@code notarist.storage.gcs.auto-create-bucket=true} (the
 * default) only where the credential may create buckets — e.g. local dev against an emulator.
 *
 * <p><b>Failure policy:</b> loud, not fatal — a missing bucket or a permission error is logged at
 * ERROR and marks GCS degraded so {@code /actuator/health} reports DOWN at startup, rather than
 * the first upload failing mysteriously later. The rest of the platform still boots.
 */
@Component
@Order(0)
public class GcsBucketProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GcsBucketProvisioner.class);

    private final Storage              storage;
    private final GcsProperties        props;
    private final DegradedModeRegistry degradedMode;
    private final String               location;

    public GcsBucketProvisioner(@Lazy Storage storage,
                                GcsProperties props,
                                DegradedModeRegistry degradedMode,
                                @Value("${notarist.storage.gcs.location:US}") String location) {
        this.storage      = storage;
        this.props        = props;
        this.degradedMode = degradedMode;
        this.location     = location;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucketName = props.defaultBucket();
        try {
            Bucket bucket = storage.get(bucketName);
            if (bucket != null) {
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
                log.info("GCS bucket present: {} (location={})", bucketName, bucket.getLocation());
                return;
            }

            // An emulator bucket has no other provisioner. Terraform does not manage fake-gcs, and
            // auto-create-bucket ships as false (correctly — the Cloud Run SA holds objectAdmin, not
            // storage.buckets.create). Requiring an extra flag to get a bucket that only this process
            // can create makes a cold start need a manual step, so emulator mode implies create.
            // Production is untouched: emulatorEnabled() is only true when STORAGE_EMULATOR_HOST is set.
            boolean mayCreate = Boolean.TRUE.equals(props.autoCreateBucket()) || props.emulatorEnabled();

            if (!mayCreate) {
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS,
                        "bucket missing: " + bucketName);
                log.error("GCS bucket MISSING: {} and auto-create is disabled — provision it "
                        + "(terraform/gcloud) and grant the runtime SA roles/storage.objectAdmin. "
                        + "Uploads/OCR/chunk storage will not work until this is resolved.", bucketName);
                return;
            }

            storage.create(BucketInfo.newBuilder(bucketName).setLocation(location).build());
            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
            log.info("GCS bucket created: {} (location={})", bucketName, location);

        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
            log.error("GCS bucket provisioning FAILED bucket={}: {} — check GCS_BUCKET, the runtime "
                    + "service account's IAM roles (roles/storage.objectAdmin), and GOOGLE_CLOUD_PROJECT.",
                    bucketName, e.getMessage(), e);
        }
    }
}
