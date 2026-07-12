package com.notarist.infra.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO connection properties.
 * Bound from notarist.storage.minio.* in application.yaml (endpoint, access-key, secret-key, buckets.*).
 */
@ConfigurationProperties(prefix = "notarist.storage.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        Buckets buckets,
        boolean secure,
        int connectTimeoutMs,
        int readTimeoutMs,
        int writeTimeoutMs,
        int maxRetries
) {
    public MinioProperties {
        if (endpoint == null || endpoint.isBlank()) throw new IllegalStateException("notarist.storage.minio.endpoint is required");
        if (accessKey == null || accessKey.isBlank()) throw new IllegalStateException("notarist.storage.minio.access-key is required");
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("notarist.storage.minio.secret-key is required");
        if (buckets == null) buckets = new Buckets(null, null, null, null, null);
        if (connectTimeoutMs <= 0) connectTimeoutMs = 5_000;
        if (readTimeoutMs <= 0)    readTimeoutMs    = 30_000;
        if (writeTimeoutMs <= 0)   writeTimeoutMs   = 120_000;
        if (maxRetries <= 0)       maxRetries       = 3;
    }

    /** Raw document storage bucket — used by DocumentStoragePort for upload/confirm/download. */
    public String defaultBucket() {
        return buckets.raw();
    }

    public record Buckets(String raw, String ocr, String processed, String chunk, String export) {
        public Buckets {
            if (raw == null || raw.isBlank())             raw = "notarist-raw";
            if (ocr == null || ocr.isBlank())             ocr = "notarist-ocr";
            if (processed == null || processed.isBlank()) processed = "notarist-processed";
            if (chunk == null || chunk.isBlank())         chunk = "notarist-chunk";
            if (export == null || export.isBlank())       export = "notarist-export";
        }
    }
}
