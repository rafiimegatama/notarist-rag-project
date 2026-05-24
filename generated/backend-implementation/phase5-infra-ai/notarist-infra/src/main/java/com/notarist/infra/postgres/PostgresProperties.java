package com.notarist.infra.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PostgreSQL connection properties.
 * Bound from notarist.infra.datasource.postgres.* in application.yaml.
 */
@ConfigurationProperties(prefix = "notarist.infra.datasource.postgres")
public record PostgresProperties(
        String url,
        String username,
        String password,
        String driverClassName,
        int minimumIdle,
        int maximumPoolSize,
        long connectionTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs,
        long keepaliveTimeMs,
        String poolName,
        String schema
) {
    public PostgresProperties {
        if (url == null || url.isBlank())           throw new IllegalStateException("spring.datasource.postgres.url required");
        if (username == null || username.isBlank()) throw new IllegalStateException("spring.datasource.postgres.username required");
        if (driverClassName == null || driverClassName.isBlank()) driverClassName = "org.postgresql.Driver";
        if (minimumIdle <= 0)          minimumIdle        = 2;
        if (maximumPoolSize <= 0)      maximumPoolSize     = 10;
        if (connectionTimeoutMs <= 0)  connectionTimeoutMs = 5_000;
        if (idleTimeoutMs <= 0)        idleTimeoutMs       = 600_000;
        if (maxLifetimeMs <= 0)        maxLifetimeMs       = 1_800_000;
        if (keepaliveTimeMs <= 0)      keepaliveTimeMs     = 60_000;
        if (poolName == null || poolName.isBlank()) poolName = "notarist-postgres-pool";
        if (schema == null || schema.isBlank())    schema = "public";
    }
}
