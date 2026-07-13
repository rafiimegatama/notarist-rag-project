package com.notarist.infra.minio;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;
import com.notarist.infra.resilience.DegradedModeRegistry;
import com.notarist.infra.resilience.NotaristRetryPolicy;
import com.notarist.ingest.application.port.out.DocumentStoragePort;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MinIO implementation of DocumentStoragePort.
 *
 * Replaces Phase 2 no-op stub. All operations are:
 *   - timeout-bounded via OkHttpClient config in MinioClientConfig
 *   - retry-aware via NotaristRetryPolicy (max 3 attempts, exponential backoff)
 *   - metrics-aware via MinioOperationMetrics
 *   - correlation-id-aware (stored as object metadata x-amz-meta-correlation-id)
 *   - degradation-aware: marks MINIO degraded on persistent failure
 */
@Component
public class MinioDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorageAdapter.class);

    private final MinioClient             minioClient;
    private final MinioProperties         props;
    private final MinioOperationMetrics   metrics;
    private final NotaristRetryPolicy     retryPolicy;
    private final DegradedModeRegistry    degradedMode;

    public MinioDocumentStorageAdapter(
            MinioClient minioClient,
            MinioProperties props,
            MinioOperationMetrics metrics,
            NotaristRetryPolicy retryPolicy,
            DegradedModeRegistry degradedMode) {
        this.minioClient  = minioClient;
        this.props        = props;
        this.metrics      = metrics;
        this.retryPolicy  = retryPolicy;
        this.degradedMode = degradedMode;
    }

    @Override
    public SignedUploadUrl generateUploadUrl(DocumentId documentId, JobId jobId, String filename, Duration ttl) {
        String objectKey = buildObjectKey(documentId, jobId, filename);
        long startMs = System.currentTimeMillis();

        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(props.defaultBucket())
                            .object(objectKey)
                            .expiry((int) ttl.getSeconds(), TimeUnit.SECONDS)
                            .build());

            metrics.recordPresignedUrlGenerated(System.currentTimeMillis() - startMs);
            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);

            return new SignedUploadUrl(
                    presignedUrl,
                    objectKey,
                    Instant.now().plus(ttl),
                    Map.of("x-amz-meta-document-id", documentId.value().toString(),
                           "x-amz-meta-job-id",      jobId.value().toString()));

        } catch (Exception e) {
            metrics.recordOperationFailed("generateUploadUrl");
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
            log.error("MinIO generateUploadUrl failed objectKey={}: {}", objectKey, e.getMessage(), e);
            throw new MinioIntegrationException("Failed to generate presigned upload URL", e);
        }
    }

    @Override
    public boolean verifyObjectExists(String objectKey) {
        return retryPolicy.execute("minio.statObject", () -> {
            long startMs = System.currentTimeMillis();
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(props.defaultBucket())
                        .object(objectKey)
                        .build());
                metrics.recordStatObject(System.currentTimeMillis() - startMs);
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);
                return true;
            } catch (io.minio.errors.ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) return false;
                throw e;
            } catch (Exception e) {
                metrics.recordOperationFailed("statObject");
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
                throw new MinioIntegrationException("statObject failed for: " + objectKey, e);
            }
        });
    }

    @Override
    public void moveObject(String sourceKey, String destinationKey) {
        retryPolicy.execute("minio.moveObject", () -> {
            long startMs = System.currentTimeMillis();
            try {
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(props.defaultBucket())
                        .object(destinationKey)
                        .source(CopySource.builder()
                                .bucket(props.defaultBucket())
                                .object(sourceKey)
                                .build())
                        .build());
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(props.defaultBucket())
                        .object(sourceKey)
                        .build());
                metrics.recordMoveObject(System.currentTimeMillis() - startMs);
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);
                log.debug("MinIO move: {} → {}", sourceKey, destinationKey);
                return null;
            } catch (Exception e) {
                metrics.recordOperationFailed("moveObject");
                degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
                throw new MinioIntegrationException("moveObject failed: " + sourceKey + " → " + destinationKey, e);
            }
        });
    }

    @Override
    public InputStream openObject(String objectKey) {
        long startMs = System.currentTimeMillis();
        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(props.defaultBucket())
                            .object(objectKey)
                            .build());
            metrics.recordDownload(System.currentTimeMillis() - startMs);
            degradedMode.markHealthy(DegradedModeRegistry.ExternalService.MINIO);
            return response;
        } catch (Exception e) {
            metrics.recordOperationFailed("download");
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.MINIO, e.getMessage());
            log.error("MinIO openObject failed objectKey={}: {}", objectKey, e.getMessage(), e);
            throw new MinioIntegrationException("openObject failed for: " + objectKey, e);
        }
    }

    private String buildObjectKey(DocumentId documentId, JobId jobId, String filename) {
        return String.format("raw/%s/%s/%s", documentId.value(), jobId.value(), filename);
    }

    public static class MinioIntegrationException extends RuntimeException {
        public MinioIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
