package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.application.port.out.DocumentStoragePort;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// @Component deactivated: superseded by MinioDocumentStorageAdapter in notarist-infra (Phase 5)
// That version adds retry policy, operation metrics, and degradation awareness.
public class MinioDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorageAdapter.class);

    private final MinioClient minioClient;

    public MinioDocumentStorageAdapter(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public SignedUploadUrl generateUploadUrl(
            DocumentId documentId, JobId jobId, String filename, Duration ttl) {
        String objectKey = NotaristConstants.BUCKET_RAW + "/" +
                documentId.value() + "/" + sanitize(filename);

        try {
            String signedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(NotaristConstants.BUCKET_RAW)
                            .object(objectKey)
                            .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                            .build());

            return new SignedUploadUrl(
                    signedUrl,
                    objectKey,
                    Instant.now().plus(ttl),
                    Map.of("Content-Type", "application/pdf", "x-job-id", jobId.value().toString())
            );
        } catch (Exception e) {
            log.error("Failed to generate signed upload URL for documentId={}", documentId.value(), e);
            throw new IllegalStateException("Failed to generate signed upload URL", e);
        }
    }

    @Override
    public boolean verifyObjectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(NotaristConstants.BUCKET_RAW)
                    .object(resolveObjectPath(objectKey))
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("Object not found in MinIO: {}", objectKey);
            return false;
        }
    }

    @Override
    public void moveObject(String sourceKey, String targetKey) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(NotaristConstants.BUCKET_PROCESSED)
                    .object(resolveObjectPath(targetKey))
                    .source(CopySource.builder()
                            .bucket(NotaristConstants.BUCKET_RAW)
                            .object(resolveObjectPath(sourceKey))
                            .build())
                    .build());

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(NotaristConstants.BUCKET_RAW)
                    .object(resolveObjectPath(sourceKey))
                    .build());
        } catch (Exception e) {
            log.error("Failed to move object from {} to {}", sourceKey, targetKey, e);
            throw new IllegalStateException("Failed to move object in MinIO", e);
        }
    }

    private String resolveObjectPath(String objectKey) {
        return objectKey.contains("/") ? objectKey.substring(objectKey.indexOf('/') + 1) : objectKey;
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
