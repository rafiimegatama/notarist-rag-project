package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JobId;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Port for document binary storage (backed by Google Cloud Storage). */
public interface DocumentStoragePort {

    /**
     * Generates a presigned PUT URL for the raw document. The object key is the
     * canonical raw key {@code notarist-raw/{tenantId}/{documentId}} — the same key
     * {@code confirmUpload} verifies and the OCR stage reads back, so the upload
     * lands exactly where the pipeline looks for it. tenantId is required to build
     * that key.
     */
    SignedUploadUrl generateUploadUrl(UUID tenantId, DocumentId documentId, JobId jobId, String filename, Duration ttl);

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
