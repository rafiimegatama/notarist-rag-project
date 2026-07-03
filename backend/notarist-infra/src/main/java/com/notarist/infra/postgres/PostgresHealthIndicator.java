package com.notarist.infra.postgres;

import com.notarist.infra.resilience.DegradedModeRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator for PostgreSQL (search datasource).
 * Executes a lightweight SELECT 1 via the postgresJdbcTemplate.
 */
@Component
public class PostgresHealthIndicator implements HealthIndicator {

    private final JdbcTemplate         jdbcTemplate;
    private final DegradedModeRegistry degradedMode;

    public PostgresHealthIndicator(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate jdbcTemplate,
            DegradedModeRegistry degradedMode) {
        this.jdbcTemplate  = jdbcTemplate;
        this.degradedMode  = degradedMode;
    }

    @Override
    public Health health() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                degradedMode.markHealthy(DegradedModeRegistry.ExternalService.POSTGRES);
                return Health.up()
                        .withDetail("db", "PostgreSQL")
                        .withDetail("query", "SELECT 1")
                        .withDetail("degraded", false)
                        .build();
            }
            return Health.down().withDetail("reason", "SELECT 1 returned unexpected result").build();
        } catch (Exception e) {
            degradedMode.markDegraded(DegradedModeRegistry.ExternalService.POSTGRES, e.getMessage());
            return Health.down(e)
                    .withDetail("db", "PostgreSQL")
                    .withDetail("degraded", true)
                    .build();
        }
    }
}
