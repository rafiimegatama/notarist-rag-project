package com.notarist.ingest.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
public class IngestModuleConfig {

    @Bean("ingestMinioClient")
    public MinioClient minioClient(
            @Value("${notarist.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${notarist.minio.access-key:minioadmin}") String accessKey,
            @Value("${notarist.minio.secret-key:minioadmin}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("ingestPostgresDataSource")
    public DataSource ingestPostgresDataSource(
            @Value("${notarist.postgres.url:jdbc:postgresql://localhost:5432/notarist}") String url,
            @Value("${notarist.postgres.username:notarist}") String username,
            @Value("${notarist.postgres.password:notarist}") String password,
            @Value("${notarist.postgres.pool.max-size:10}") int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("IngestPostgresPool");
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }

    @Bean("ingestJdbcTemplate")
    public JdbcTemplate ingestJdbcTemplate(
            @Qualifier("ingestPostgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("ingestSchedulerTaskScheduler")
    public ThreadPoolTaskScheduler ingestSchedulerTaskScheduler(
            @Value("${notarist.ingest.scheduler.thread-pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("ingest-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
