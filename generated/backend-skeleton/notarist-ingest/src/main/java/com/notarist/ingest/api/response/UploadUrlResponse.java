package com.notarist.ingest.api.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadUrlResponse(
    UUID jobId,
    String signedUrl,
    String objectKey,
    Instant expiresAt,
    Map<String, String> uploadHeaders
) {}
