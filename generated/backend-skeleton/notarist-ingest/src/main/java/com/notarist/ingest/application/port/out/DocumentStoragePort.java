package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;

import java.time.Duration;
import java.util.Map;

/** Port for MinIO object storage operations. */
public interface DocumentStoragePort {

    SignedUploadUrl generateUploadUrl(DocumentId documentId, JobId jobId, String filename, Duration ttl);

    boolean verifyObjectExists(String objectKey);

    void moveObject(String sourceKey, String targetKey);

    record SignedUploadUrl(
        String signedUrl,
        String objectKey,
        java.time.Instant expiresAt,
        Map<String, String> requiredHeaders
    ) {}
}
