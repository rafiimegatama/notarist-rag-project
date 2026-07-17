package com.notarist.infra.postgres;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway configuration — the single migrator for the whole PostgreSQL database.
 *
 * Migration locations: classpath:db/postgres/flyway/
 * Naming convention: V{major}__{description}.sql (e.g., V3__search_postgres_schema.sql)
 *
 * This bean's existence makes Spring Boot's own Flyway auto-configuration back off
 * (it is @ConditionalOnMissingBean(Flyway.class)), so there is exactly one Flyway against the
 * database and no chance of a second history table applying the same migrations twice.
 * It covers what Liquibase/Oracle used to own as well: notarist_user, dokumen_legal and
 * ingestion_job (V8) and the tenant-isolation RLS policies (V9).
 *
 * Baseline on migrate: true — safe for existing PostgreSQL databases that predate Flyway tracking.
 */
@Configuration
public class FlywaySearchConfig {

    @Bean("flywaySearch")
    public Flyway flywaySearch(@Qualifier("postgresDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/postgres/flyway")
                .table("flyway_schema_history_search")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();

        flyway.migrate();
        return flyway;
    }
}
