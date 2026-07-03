package com.notarist.search.config;

import org.springframework.context.annotation.Configuration;

/**
 * Search module Spring configuration.
 *
 * PostgreSQL datasource beans (postgresDataSource, postgresJdbcTemplate) are
 * provided centrally by notarist-infra PostgresConnectionConfig so they appear
 * once in the application context — avoiding BeanDefinitionStoreException on startup.
 *
 * BM25SearchRepositoryImpl injects @Qualifier("postgresJdbcTemplate") which
 * resolves to the HikariCP-backed bean from PostgresConnectionConfig.
 */
@Configuration
public class SearchModuleConfig {
}
