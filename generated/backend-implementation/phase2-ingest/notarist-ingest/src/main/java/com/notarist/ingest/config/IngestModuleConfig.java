package com.notarist.ingest.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Ingest module configuration.
 *
 * PHASE 6A.3-FIX:
 *   - Namespace updated: notarist.minio.* → notarist.infra.minio.*
 *   - Namespace updated: notarist.postgres.* → notarist.infra.datasource.postgres.*
 *   - Removed hardcoded credential defaults (SEC-01, SEC-02)
 *   - Removed duplicate @Bean("postgresJdbcTemplate") — use notarist-infra's PostgresConnectionConfig
 *   - Removed duplicate @Bean("ingestPostgresDataSource") — use notarist-infra's postgresDataSource
 *   - ingestMinioClient kept as named bean for ingest-specific MinIO operations
 *   - ingestSchedulerTaskScheduler kept — ingest owns its own scheduler thread pool
 */
@Configuration
@EnableScheduling
public class IngestModuleConfig {

    @Bean("ingestMinioClient")
    public MinioClient minioClient(
            @Value("${notarist.infra.minio.endpoint}") String endpoint,
            @Value("${notarist.infra.minio.access-key}") String accessKey,
            @Value("${notarist.infra.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("ingestSchedulerTaskScheduler")
    public ThreadPoolTaskScheduler ingestSchedulerTaskScheduler(
            @Value("${notarist.ingest.scheduler.thread-pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("ingest-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }
}
