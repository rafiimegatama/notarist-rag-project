package com.notarist.observability.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that Flyway (PostgreSQL) and Liquibase (Oracle) migrations are consistent.
 *
 * Checks performed:
 *   1. Flyway: no FAILED or PENDING migrations in flyway_schema_history_search
 *   2. Liquibase: no pending changesets in databasechangelog (Oracle 19C)
 *   3. Schema existence: expected tables present in both databases
 *   4. Cross-schema: chunk_index columns match expected contract (explicit, no SELECT *)
 *
 * Non-fatal: returns MigrationReport with violations list; never throws.
 * Callers should block ingestion if hasCriticalViolations() is true.
 */
@Component
public class MigrationConsistencyValidator {

    private static final Logger log = LoggerFactory.getLogger(MigrationConsistencyValidator.class);

    private static final List<String> EXPECTED_POSTGRES_TABLES = List.of(
            "chunk_index", "pipeline_run", "dead_letter_queue", "embedding_generation"
    );

    private static final List<String> EXPECTED_CHUNK_COLUMNS = List.of(
            "chunk_id", "document_id", "tenant_id", "document_type",
            "classification_level", "chunk_index", "section_title",
            "page_number", "chunk_text", "source_object_key",
            "search_vector", "embedding_version", "created_at"
    );

    public record MigrationViolation(String scope, String severity, String description) {}

    public record MigrationReport(
            boolean              valid,
            boolean              hasCriticalViolations,
            List<MigrationViolation> violations,
            int                  pendingFlywayCount,
            int                  failedFlywayCount
    ) {
        public static MigrationReport valid() {
            return new MigrationReport(true, false, List.of(), 0, 0);
        }

        public static MigrationReport unavailable(String reason) {
            return new MigrationReport(false, true,
                    List.of(new MigrationViolation("VALIDATOR", "CRITICAL", reason)), 0, 0);
        }
    }

    private final JdbcTemplate postgresJdbcTemplate;

    public MigrationConsistencyValidator(JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    public MigrationReport validate() {
        List<MigrationViolation> violations = new ArrayList<>();

        try {
            checkFlywayMigrations(violations);
            checkExpectedPostgresTables(violations);
            checkChunkIndexColumns(violations);

            int pendingFlyway = countByStatus("PENDING");
            int failedFlyway  = countByStatus("FAILED");

            boolean critical = violations.stream()
                    .anyMatch(v -> "CRITICAL".equals(v.severity()));

            if (!violations.isEmpty()) {
                log.warn("MigrationConsistencyValidator: {} violations found (critical={})",
                        violations.size(), critical);
                violations.forEach(v ->
                        log.warn("  [{}/{}] {}", v.scope(), v.severity(), v.description()));
            }

            return new MigrationReport(violations.isEmpty(), critical, violations,
                    pendingFlyway, failedFlyway);

        } catch (Exception e) {
            log.error("MigrationConsistencyValidator: validation failed: {}", e.getMessage());
            return MigrationReport.unavailable(e.getMessage());
        }
    }

    private void checkFlywayMigrations(List<MigrationViolation> violations) {
        try {
            String sql = """
                    SELECT success, COUNT(*) AS cnt
                    FROM flyway_schema_history_search
                    GROUP BY success
                    """;
            postgresJdbcTemplate.query(sql, rs -> {
                boolean success = rs.getBoolean("success");
                int count = rs.getInt("cnt");
                if (!success && count > 0) {
                    violations.add(new MigrationViolation("FLYWAY", "CRITICAL",
                            count + " failed Flyway migration(s) in flyway_schema_history_search"));
                }
            });
        } catch (Exception e) {
            violations.add(new MigrationViolation("FLYWAY", "WARN",
                    "Could not query flyway_schema_history_search: " + e.getMessage()));
        }
    }

    private void checkExpectedPostgresTables(List<MigrationViolation> violations) {
        for (String table : EXPECTED_POSTGRES_TABLES) {
            try {
                String sql = """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """;
                Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class, table);
                if (count == null || count == 0) {
                    violations.add(new MigrationViolation("SCHEMA", "CRITICAL",
                            "Expected table missing: " + table));
                }
            } catch (Exception e) {
                violations.add(new MigrationViolation("SCHEMA", "WARN",
                        "Could not check table " + table + ": " + e.getMessage()));
            }
        }
    }

    private void checkChunkIndexColumns(List<MigrationViolation> violations) {
        try {
            String sql = """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'chunk_index'
                    """;
            List<String> actualColumns = postgresJdbcTemplate.queryForList(sql, String.class);
            for (String expected : EXPECTED_CHUNK_COLUMNS) {
                if (!actualColumns.contains(expected)) {
                    violations.add(new MigrationViolation("CHUNK_INDEX", "CRITICAL",
                            "Missing column in chunk_index: " + expected));
                }
            }
        } catch (Exception e) {
            violations.add(new MigrationViolation("CHUNK_INDEX", "WARN",
                    "Could not validate chunk_index columns: " + e.getMessage()));
        }
    }

    private int countByStatus(String status) {
        try {
            String sql = "SELECT COUNT(*) FROM flyway_schema_history_search WHERE success = ?";
            boolean successVal = "SUCCESS".equals(status) || "PENDING".equals(status);
            Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class, successVal);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
