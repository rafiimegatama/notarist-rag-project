package com.notarist.infra.postgres;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway configuration scoped to the PostgreSQL search schema.
 *
 * Migration locations: classpath:db/postgres/flyway/
 * Naming convention: V{major}__{description}.sql (e.g., V3__search_postgres_schema.sql)
 *
 * Separate from Liquibase which manages Oracle 19C schema.
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
