package com.notarist.web.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Oracle (primary) DataSource.
 *
 * <p>Backed by HikariCP (on the classpath via spring-boot-starter-data-jpa). The previous
 * implementation handed back a bare {@code oracle.jdbc.pool.OracleDataSource}, which despite
 * its package name is NOT a connection pool: every {@code getConnection()} opened a fresh
 * physical session, and the configured {@code notarist.database.oracle.pool-max} /
 * {@code pool-min} were silently ignored — no bounded pool, no acquisition timeout.
 *
 * <p>Oracle VPD identity is applied per transaction by the VpdContextApplier components
 * (they call {@code NOTARIST.SET_NOTARIST_CTX.set_identity} on the borrowed connection and
 * clear it before completion), so no pool-level connection-init SQL is required here.
 */
@Configuration
public class DataSourceConfig {

    @Value("${notarist.database.oracle.url}")
    private String oracleUrl;

    @Value("${notarist.database.oracle.username}")
    private String oracleUsername;

    @Value("${notarist.database.oracle.password}")
    private String oraclePassword;

    @Value("${notarist.database.oracle.pool-max:10}")
    private int oraclePoolMax;

    @Value("${notarist.database.oracle.pool-min:2}")
    private int oraclePoolMin;

    /** Max wait for a connection from the pool before failing fast. */
    @Value("${notarist.database.oracle.connection-timeout-ms:30000}")
    private long oracleConnectionTimeoutMs;

    /** How long an idle connection above pool-min may live. */
    @Value("${notarist.database.oracle.idle-timeout-ms:600000}")
    private long oracleIdleTimeoutMs;

    /** Retire connections before any DB/firewall side idle-kill. */
    @Value("${notarist.database.oracle.max-lifetime-ms:1800000}")
    private long oracleMaxLifetimeMs;

    @Value("${notarist.database.oracle.validation-timeout-ms:5000}")
    private long oracleValidationTimeoutMs;

    @Bean(destroyMethod = "close")
    @Primary
    public DataSource oracleDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("notarist-oracle-pool");
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setJdbcUrl(oracleUrl);
        config.setUsername(oracleUsername);
        config.setPassword(oraclePassword);

        config.setMaximumPoolSize(oraclePoolMax);
        // Hikari clamps minimumIdle to maximumPoolSize itself, but guard explicitly so a
        // mis-set pool-min > pool-max cannot silently invert the intent.
        config.setMinimumIdle(Math.min(oraclePoolMin, oraclePoolMax));

        config.setConnectionTimeout(oracleConnectionTimeoutMs);
        config.setIdleTimeout(oracleIdleTimeoutMs);
        config.setMaxLifetime(oracleMaxLifetimeMs);
        config.setValidationTimeout(oracleValidationTimeoutMs);

        // ojdbc11 implements JDBC4 Connection.isValid(), so no connectionTestQuery is needed.
        // autoCommit is left at Hikari's default (true), matching Spring Boot's own datasource
        // defaults: Hibernate/Spring turn it off for the duration of each managed transaction,
        // while any non-transactional JdbcTemplate write keeps committing as it does today.

        return new HikariDataSource(config);
    }
}
