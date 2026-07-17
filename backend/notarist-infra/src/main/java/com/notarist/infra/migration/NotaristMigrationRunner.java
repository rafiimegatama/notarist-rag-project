package com.notarist.infra.migration;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates migration state on startup after the datasource is ready.
 *
 * Flyway owns the whole PostgreSQL database — auth, documents, ingest, search, audit and the
 * tenant-isolation RLS policies. (It used to share the job with Liquibase/Oracle; that half is
 * gone, so there is a single migration state to report.)
 *
 * This runner does NOT execute migrations — Flyway.migrate() is called in FlywaySearchConfig.
 * It validates that the datastore is in an expected migration state and logs a clear status
 * for operations observability.
 *
 * If validation fails, the application continues but logs a WARNING.
 * Hard failures should be surfaced via MigrationHealthIndicator, not by crashing on startup.
 */
@Component
public class NotaristMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotaristMigrationRunner.class);

    private final Flyway flywaySearch;

    public NotaristMigrationRunner(@Qualifier("flywaySearch") Flyway flywaySearch) {
        this.flywaySearch = flywaySearch;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateMigrations() {
        validatePostgresMigrations();
    }

    private void validatePostgresMigrations() {
        try {
            var info = flywaySearch.info();
            var pending = info.pending();
            var current = info.current();

            if (pending.length > 0) {
                log.warn("PostgreSQL search schema has {} pending migration(s). " +
                         "Current version: {}. Run migrations manually if auto-migration is disabled.",
                        pending.length, current != null ? current.getVersion() : "none");
            } else {
                log.info("PostgreSQL search schema is up to date. Version: {}",
                        current != null ? current.getVersion() : "baseline");
            }
        } catch (Exception e) {
            log.error("Failed to validate PostgreSQL migrations: {}", e.getMessage(), e);
        }
    }
}
