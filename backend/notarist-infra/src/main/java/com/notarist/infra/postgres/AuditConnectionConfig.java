package com.notarist.infra.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Dedicated audit HikariCP DataSource — same Supabase PostgreSQL as the business pool
 * ({@link PostgresConnectionConfig}), separate pool.
 *
 * <p><b>Why a third pool.</b> The audit write is fail-closed for AUTH/SECURITY/DOCUMENT events —
 * an audit that cannot be written fails the business operation. When it drew a second connection
 * from the shared business pool (via {@code REQUIRES_NEW}), N concurrent audited operations each
 * holding one connection and waiting for another exhausted the pool
 * (proven by {@code AuditRequiresNewPoolExhaustionIT}). A small, dedicated pool decouples the audit
 * write from business (and ingest) load, so a saturated business pool can never starve the audit.
 *
 * <p><b>autocommit=true</b>: the append is a single INSERT. On an autocommit connection it commits
 * immediately and independently of the caller's transaction — the same "commit independently of the
 * caller's outcome" guarantee the old {@code REQUIRES_NEW} provided, but without transaction
 * suspension and without a second connection from the business pool.
 */
@Configuration
public class AuditConnectionConfig {

    @Bean("auditDataSource")
    public DataSource auditDataSource(PostgresProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.url());
        config.setUsername(props.username());
        config.setPassword(props.password());
        config.setDriverClassName(props.driverClassName());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setAutoCommit(true);
        config.setPoolName("AuditPostgresPool");
        config.setSchema(props.schema());
        return new HikariDataSource(config);
    }

    @Bean("auditJdbcTemplate")
    public JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
