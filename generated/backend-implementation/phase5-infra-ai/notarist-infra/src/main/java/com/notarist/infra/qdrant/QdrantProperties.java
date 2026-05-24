package com.notarist.infra.qdrant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant connection properties.
 * Bound from notarist.infra.qdrant.* in application.yaml.
 */
@ConfigurationProperties(prefix = "notarist.infra.qdrant")
public record QdrantProperties(
        String host,
        int port,
        String grpcPort,
        String apiKey,
        String collectionName,
        int connectTimeoutMs,
        int searchTimeoutMs,
        int upsertTimeoutMs,
        int maxRetries,
        float defaultMinScore
) {
    public QdrantProperties {
        if (host == null || host.isBlank())           host = "localhost";
        if (port <= 0)                                port = 6333;
        if (collectionName == null || collectionName.isBlank()) collectionName = "notarist-chunks";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3_000;
        if (searchTimeoutMs  <= 0) searchTimeoutMs  = 5_000;
        if (upsertTimeoutMs  <= 0) upsertTimeoutMs  = 10_000;
        if (maxRetries       <= 0) maxRetries       = 3;
        if (defaultMinScore  <= 0) defaultMinScore  = 0.60f;
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    public String collectionUrl() {
        return baseUrl() + "/collections/" + collectionName;
    }
}
