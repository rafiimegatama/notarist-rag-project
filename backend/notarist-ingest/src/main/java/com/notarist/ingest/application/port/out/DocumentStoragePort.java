package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Port for document binary storage (MinIO). */
public interface DocumentStoragePort {

    SignedUploadUrl generateUploadUrl(DocumentId documentId, JobId jobId, String filename, Duration ttl);

    boolean verifyObjectExists(String objectKey);

    void moveObject(String sourceKey, String destinationKey);

    /**
     * Opens the stored object for reading. Used by pipeline workers to consume
     * prior-stage output (e.g. ChunkWorker reading the OCR-extracted text).
     * Caller owns the stream and must close it.
     */
    InputStream openObject(String objectKey);

    record SignedUploadUrl(
            String signedUrl,
            String objectKey,
            Instant expiresAt,
            Map<String, String> requiredHeaders
    ) {}
}
