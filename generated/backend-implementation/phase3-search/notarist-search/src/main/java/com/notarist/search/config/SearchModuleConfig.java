package com.notarist.search.config;

import org.springframework.context.annotation.Configuration;

/**
 * Search module configuration.
 *
 * PHASE 6A.3-FIX:
 *   - Removed @Bean("postgresDataSource") — owned by notarist-infra PostgresConnectionConfig
 *   - Removed @Bean("postgresJdbcTemplate") — owned by notarist-infra PostgresConnectionConfig
 *   - Removed @Bean("postgresDataSourceProperties") — bound to spring.datasource.postgres.* (orphaned)
 *
 * BM25SearchRepositoryImpl injects @Qualifier("postgresJdbcTemplate") which resolves to
 * notarist-infra's PostgresConnectionConfig bean (HikariCP, 30s query timeout).
 *
 * Search module owns no datasource configuration — all DB access via infra-provided beans.
 */
@Configuration
public class SearchModuleConfig {
}
