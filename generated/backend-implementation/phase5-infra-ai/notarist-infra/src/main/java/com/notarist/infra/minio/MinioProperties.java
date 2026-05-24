package com.notarist.infra.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO connection properties.
 * Bound from notarist.infra.minio.* in application.yaml.
 */
@ConfigurationProperties(prefix = "notarist.infra.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String defaultBucket,
        boolean secure,
        int connectTimeoutMs,
        int readTimeoutMs,
        int writeTimeoutMs,
        int maxRetries
) {
    public MinioProperties {
        if (endpoint == null || endpoint.isBlank()) throw new IllegalStateException("spring.minio.endpoint is required");
        if (accessKey == null || accessKey.isBlank()) throw new IllegalStateException("spring.minio.access-key is required");
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("spring.minio.secret-key is required");
        if (defaultBucket == null || defaultBucket.isBlank()) defaultBucket = "notarist-documents";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 5_000;
        if (readTimeoutMs <= 0)    readTimeoutMs    = 30_000;
        if (writeTimeoutMs <= 0)   writeTimeoutMs   = 120_000;
        if (maxRetries <= 0)       maxRetries       = 3;
    }
}
