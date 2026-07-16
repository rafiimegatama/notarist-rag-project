package com.notarist.infra.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * The application's single HikariCP DataSource — Supabase PostgreSQL.
 *
 * <p>This bean is {@code @Primary} and is therefore what Spring Boot's JPA auto-configuration
 * builds the EntityManagerFactory on. It backs BOTH the JPA repositories (notarist_user,
 * dokumen_legal, ingestion_job) and the JdbcTemplate paths (BM25 search, chunk_index,
 * audit_trail, session_token) — there is no second database. It keeps the "postgresDataSource" /
 * "postgresJdbcTemplate" bean names so the existing {@code @Qualifier} injection points
 * (BM25SearchRepositoryImpl, SessionTokenRepositoryImpl, …) resolve unchanged.
 *
 * Pool sizing rationale:
 *   - minimumIdle=2: always-warm connections for BM25 search latency
 *   - maximumPoolSize=10: bounded to avoid PostgreSQL max_connections exhaustion
 *   - keepalive=60s: prevents idle connection drops behind Supabase's pooler / Cloud Run idling
 */
@Configuration
@EnableConfigurationProperties(PostgresProperties.class)
public class PostgresConnectionConfig {

    @Bean("postgresDataSource")
    @Primary
    public DataSource postgresDataSource(PostgresProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.url());
        config.setUsername(props.username());
        config.setPassword(props.password());
        config.setDriverClassName(props.driverClassName());
        config.setMinimumIdle(props.minimumIdle());
        config.setMaximumPoolSize(props.poolMax());
        config.setConnectionTimeout(props.connectionTimeoutMs());
        config.setIdleTimeout(props.idleTimeoutMs());
        config.setMaxLifetime(props.maxLifetimeMs());
        config.setKeepaliveTime(props.keepaliveTimeMs());
        config.setPoolName(props.poolName());
        config.setSchema(props.schema());

        // Lightweight connection test query
        config.setConnectionTestQuery("SELECT 1");

        // Register HikariCP pool metrics with Micrometer
        config.setMetricRegistry(null);  // Micrometer integration via HikariCP auto-config

        return new HikariDataSource(config);
    }

    @Bean("postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(30);  // 30 seconds per query — aligns with IntegrationTimeouts
        return template;
    }
}
