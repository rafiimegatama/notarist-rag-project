package com.notarist.web.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Primary Oracle DataSource with HikariCP pooling.
 * Namespace: notarist.infra.datasource.oracle.*
 *
 * Uses OracleDataSource as the underlying JDBC datasource wrapped in HikariCP.
 * VPD context injection occurs in VpdContextApplier (per-request, not pool-level).
 * Connection test uses "SELECT 1 FROM DUAL" (Oracle-specific).
 *
 * Pool sizing defaults:
 *   max=20  — adequate for 3 concurrent ingest workers + web layer
 *   min=5   — keep warm connections; cold-start latency on Oracle JDBC is ~200ms
 */
@Configuration
public class DataSourceConfig {

    @Value("${notarist.infra.datasource.oracle.url}")
    private String oracleUrl;

    @Value("${notarist.infra.datasource.oracle.username}")
    private String oracleUsername;

    @Value("${notarist.infra.datasource.oracle.password}")
    private String oraclePassword;

    @Value("${notarist.infra.datasource.oracle.pool-max:20}")
    private int oraclePoolMax;

    @Value("${notarist.infra.datasource.oracle.pool-min:5}")
    private int oraclePoolMin;

    @Bean
    @Primary
    public DataSource oracleDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDataSourceClassName("oracle.jdbc.pool.OracleDataSource");
        config.addDataSourceProperty("URL", oracleUrl);
        config.addDataSourceProperty("user", oracleUsername);
        config.addDataSourceProperty("password", oraclePassword);
        config.setMaximumPoolSize(oraclePoolMax);
        config.setMinimumIdle(oraclePoolMin);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");
        config.setPoolName("notarist-oracle-pool");
        return new HikariDataSource(config);
    }
}
