package com.notarist.infra.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Health indicator exposing PostgreSQL Flyway migration state.
 * DOWN if there are pending or failed migrations.
 */
@Component
public class MigrationHealthIndicator implements HealthIndicator {

    private final Flyway flywaySearch;

    public MigrationHealthIndicator(@Qualifier("flywaySearch") Flyway flywaySearch) {
        this.flywaySearch = flywaySearch;
    }

    @Override
    public Health health() {
        try {
            MigrationInfo[] pending  = flywaySearch.info().pending();
            MigrationInfo[] failed   = Arrays.stream(flywaySearch.info().all())
                    .filter(m -> m.getState() == MigrationState.FAILED)
                    .toArray(MigrationInfo[]::new);
            MigrationInfo   current  = flywaySearch.info().current();

            if (failed.length > 0) {
                return Health.down()
                        .withDetail("failedMigrations", failed.length)
                        .withDetail("latestFailed", failed[0].getScript())
                        .build();
            }
            if (pending.length > 0) {
                return Health.status("DEGRADED")
                        .withDetail("pendingMigrations", pending.length)
                        .withDetail("currentVersion", current != null ? current.getVersion().toString() : "none")
                        .build();
            }

            return Health.up()
                    .withDetail("currentVersion", current != null ? current.getVersion().toString() : "baseline")
                    .withDetail("pendingMigrations", 0)
                    .build();

        } catch (Exception e) {
            return Health.down(e).withDetail("reason", "Cannot read Flyway migration info").build();
        }
    }
}
