package com.notarist.search.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Search module datasource configuration.
 *
 * Two datasources are active in this module:
 *   - Primary (auto-configured): Oracle 19C via spring.datasource — JPA entities, VPD, Liquibase
 *   - Secondary (explicit): PostgreSQL via spring.datasource.postgres — BM25 keyword search, Flyway
 *
 * The PostgreSQL JdbcTemplate is exposed as "postgresJdbcTemplate" so that
 * BM25SearchRepositoryImpl can @Qualifier-inject it without touching the Oracle datasource.
 */
@Configuration
public class SearchModuleConfig {

    @Bean("postgresDataSourceProperties")
    @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("postgresDataSource")
    public DataSource postgresDataSource(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(
            @Qualifier("postgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
