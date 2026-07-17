package com.notarist.infra.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;
import com.notarist.infra.resilience.DegradedModeRegistry;
import com.notarist.infra.resilience.NotaristRetryPolicy;
import com.notarist.ingest.application.port.out.DocumentStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage implementation of {@link DocumentStoragePort}.
 *
 * <p>Drop-in replacement for the former MinIO adapter — the port, its callers, and the object-key
 * layout ({@code notarist-raw/{tenantId}/{documentId}} and the downstream stage prefixes) are all
 * unchanged. Only the backing store moved from a localhost MinIO container to GCS.
 *
 * <p>All operations are:
 * <ul>
 *   <li>timeout-bounded via {@link com.google.cloud.http.HttpTransportOptions} in {@link GcsClientConfig}</li>
 *   <li>retry-aware via {@link NotaristRetryPolicy} (max 3 attempts, exponential backoff)</li>
 *   <li>metrics-aware via {@link GcsOperationMetrics}</li>
 *   <li>degradation-aware: marks GCS degraded on persistent failure</li>
 * </ul>
 *
 * <p>Uploads use <b>V4 signed PUT URLs</b>: the browser/client PUTs the bytes straight to GCS, so
 * the document body never transits Cloud Run — essential on Cloud Run's per-request memory and the
 * 32&nbsp;MiB request-body cap.
 */
@Component
public class GcsDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(GcsDocumentStorageAdapter.class);

    private final Storage                storage;
    private final GcsProperties          props;
    private final GcsOperationMetrics     metrics;
    private final NotaristRetryPolicy     retryPolicy;
    private final DegradedModeRegistry    degradedMode;

    public GcsDocumentStorageAdapter(
            @Lazy Storage storage,
            GcsProperties props,
            GcsOperationMetrics metrics,
            NotaristRetryPolicy retryPolicy,
            DegradedModeRegistry degradedMode) {
        this.storage      = storage;
        this.props        = props;
        this.metrics      = metrics;
        this.retryPolicy  = retryPolicy;
        this.degradedMode = degradedMode;
    }

    @Override
    public SignedUploadUrl generateUploadUrl(UUID tenantId, DocumentId documentId, JobId jobId, String filename, Duration ttl) {
        String objectKey = buildObjectKey(tenantId, documentId);
        long startMs = System.currentTimeMillis();

        try {
            if (props.emulatorEnabled()) {
                return emulatorUploadUrl(objectKey, documentId, jobId, ttl, startMs);
            }

            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(props.defaultBucket(), objectKey)).build();

            URL signedUrl = storage.signUrl(
                    blobInfo,
                    ttl.getSeconds(), TimeUnit.SECONDS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withV4Signature());

            metrics.recordSignedUrlGenerated(System.currentTimeMillis() - startMs);
            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);

            // No required headers: the V4 signature covers only host + method, so a bare
            // `PUT <signedUrl>` with the raw body succeeds. document-id/job-id are returned as
            // informational correlation only — sending them as signed x-goog-meta-* headers would
            // force the client to reproduce them exactly or GCS rejects the signature.
            return new SignedUploadUrl(
                    signedUrl.toString(),
                    objectKey,
                    Instant.now().plus(ttl),
                    Map.of("x-goog-meta-document-id", documentId.value().toString(),
                           "x-goog-meta-job-id",      jobId.value().toString()));

        } catch (Exception e) {
            metrics.recordOperationFailed("generateUploadUrl");
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
            log.error("GCS generateUploadUrl failed objectKey={}: {}", objectKey, e.getMessage(), e);
            throw new GcsIntegrationException("Failed to generate signed upload URL", e);
        }
    }

    /**
     * Upload URL for a GCS emulator (fake-gcs-server). Unsigned, and a POST rather than a PUT.
     *
     * <p>Neither half of the production shape survives against an emulator, and neither can be
     * worked around:
     * <ul>
     *   <li><b>Unsigned</b> — {@code signUrl} needs a {@code ServiceAccountSigner}, and emulator mode
     *       resolves no credentials at all (that is the point of it). Signing is also meaningless to
     *       a server that verifies nothing.</li>
     *   <li><b>POST, not PUT</b> — fake-gcs-server implements the JSON API only. A PUT to the XML
     *       endpoint that a V4 signed URL names returns 404, verified against fake-gcs 1.49.</li>
     * </ul>
     *
     * <p>So the emulator client uploads with {@code POST <url>} and the raw bytes as the body. This
     * is a development affordance: the URL is unguessable-by-signature nowhere and expires nowhere.
     * Production is unaffected — it takes the signed-PUT branch above, and
     * {@code emulatorEnabled()} can only be true if STORAGE_EMULATOR_HOST is deliberately set.
     */
    private SignedUploadUrl emulatorUploadUrl(
            String objectKey, DocumentId documentId, JobId jobId, Duration ttl, long startMs) {

        String uploadUrl = props.emulatorBaseUrl()
                + "/upload/storage/v1/b/" + props.defaultBucket()
                + "/o?uploadType=media&name="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);

        metrics.recordSignedUrlGenerated(System.currentTimeMillis() - startMs);
        degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);

        log.warn("GCS EMULATOR upload URL (unsigned, POST) objectKey={}", objectKey);

        return new SignedUploadUrl(
                uploadUrl,
                objectKey,
                Instant.now().plus(ttl),
                Map.of("x-goog-meta-document-id", documentId.value().toString(),
                       "x-goog-meta-job-id",      jobId.value().toString(),
                       // The record carries no method field and the production contract is an
                       // implicit PUT; say so explicitly rather than let a client guess wrong.
                       "x-notarist-upload-method", "POST"));
    }

    @Override
    public boolean verifyObjectExists(String objectKey) {
        return retryPolicy.execute("gcs.statObject", () -> {
            long startMs = System.currentTimeMillis();
            try {
                Blob blob = storage.get(BlobId.of(props.defaultBucket(), objectKey));
                metrics.recordStatObject(System.currentTimeMillis() - startMs);
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
                return blob != null && blob.exists();
            } catch (Exception e) {
                metrics.recordOperationFailed("statObject");
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
                throw new GcsIntegrationException("statObject failed for: " + objectKey, e);
            }
        });
    }

    @Override
    public void moveObject(String sourceKey, String destinationKey) {
        retryPolicy.execute("gcs.moveObject", () -> {
            long startMs = System.currentTimeMillis();
            try {
                BlobId source = BlobId.of(props.defaultBucket(), sourceKey);
                BlobId target = BlobId.of(props.defaultBucket(), destinationKey);
                // Server-side copy (no bytes through this process), then delete the source.
                storage.copy(Storage.CopyRequest.of(source, target)).getResult();
                storage.delete(source);
                metrics.recordMoveObject(System.currentTimeMillis() - startMs);
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
                log.debug("GCS move: {} → {}", sourceKey, destinationKey);
                return null;
            } catch (Exception e) {
                metrics.recordOperationFailed("moveObject");
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
                throw new GcsIntegrationException("moveObject failed: " + sourceKey + " → " + destinationKey, e);
            }
        });
    }

    @Override
    public InputStream openObject(String objectKey) {
        long startMs = System.currentTimeMillis();
        try {
            Blob blob = storage.get(BlobId.of(props.defaultBucket(), objectKey));
            if (blob == null) {
                throw new GcsIntegrationException("openObject: object not found: " + objectKey, null);
            }
            // Streamed read via a ReadChannel — the object is never fully buffered in heap, so a
            // large OCR/text object cannot blow Cloud Run's container memory. Caller closes it.
            InputStream in = Channels.newInputStream(blob.reader());
            metrics.recordDownload(System.currentTimeMillis() - startMs);
            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.GCS);
            return in;
        } catch (GcsIntegrationException e) {
            metrics.recordOperationFailed("download");
            throw e;
        } catch (Exception e) {
            metrics.recordOperationFailed("download");
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.GCS, e.getMessage());
            log.error("GCS openObject failed objectKey={}: {}", objectKey, e.getMessage(), e);
            throw new GcsIntegrationException("openObject failed for: " + objectKey, e);
        }
    }

    // Canonical raw object key — MUST match UploadOrchestrationService.confirmUpload and
    // OcrWorker, which both read notarist-raw/{tenantId}/{documentId}. The signed PUT must
    // land at exactly this key or confirmUpload's verifyObjectExists never finds the upload and
    // the pipeline dies at the OCR handoff.
    private String buildObjectKey(UUID tenantId, DocumentId documentId) {
        return "notarist-raw/" + tenantId + "/" + documentId.value();
    }

    public static class GcsIntegrationException extends RuntimeException {
        public GcsIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
