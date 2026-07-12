package com.notarist.infra.qdrant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant connection properties.
 * Bound from notarist.storage.qdrant.* in application.yaml (url, api-key, collection).
 */
@ConfigurationProperties(prefix = "notarist.storage.qdrant")
public record QdrantProperties(
        String url,
        String apiKey,
        String collection,
        int connectTimeoutMs,
        int searchTimeoutMs,
        int upsertTimeoutMs,
        int maxRetries,
        float defaultMinScore
) {
    public QdrantProperties {
        if (url == null || url.isBlank())               url = "http://localhost:6333";
        if (collection == null || collection.isBlank()) collection = "notarist-chunks";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3_000;
        if (searchTimeoutMs  <= 0) searchTimeoutMs  = 5_000;
        if (upsertTimeoutMs  <= 0) upsertTimeoutMs  = 10_000;
        if (maxRetries       <= 0) maxRetries       = 3;
        if (defaultMinScore  <= 0) defaultMinScore  = 0.60f;
    }

    public String baseUrl() {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String collectionName() {
        return collection;
    }

    public String collectionUrl() {
        return baseUrl() + "/collections/" + collection;
    }
}
