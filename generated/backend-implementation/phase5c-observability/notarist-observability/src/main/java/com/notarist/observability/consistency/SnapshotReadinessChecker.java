package com.notarist.observability.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Checks whether ingestion snapshots are current and ready for search.
 *
 * A snapshot is "ready" when:
 *   1. At least one ingestion run has completed in the last STALENESS_THRESHOLD_HOURS
 *   2. No pipeline is stuck in IN_PROGRESS beyond STUCK_THRESHOLD_MINUTES
 *   3. DLQ depth is below DLQ_WARN_THRESHOLD
 *
 * Queries PostgreSQL pipeline_run table (written by notarist-ingest Phase 2).
 * Non-fatal: returns SnapshotReadiness with degraded=true rather than throwing.
 */
@Component
public class SnapshotReadinessChecker {

    private static final Logger log = LoggerFactory.getLogger(SnapshotReadinessChecker.class);

    private static final int STALENESS_THRESHOLD_HOURS  = 24;
    private static final int STUCK_THRESHOLD_MINUTES    = 60;
    private static final int DLQ_WARN_THRESHOLD         = 10;

    public record SnapshotReadiness(
            boolean ready,
            boolean stale,
            boolean hasStuckPipelines,
            int     dlqDepth,
            Instant lastCompletedAt,
            String  diagnosis
    ) {
        public static SnapshotReadiness healthy(Instant lastCompleted) {
            return new SnapshotReadiness(true, false, false, 0, lastCompleted, "OK");
        }

        public static SnapshotReadiness unavailable(String reason) {
            return new SnapshotReadiness(false, true, false, 0, null, reason);
        }
    }

    private final JdbcTemplate postgresJdbcTemplate;

    public SnapshotReadinessChecker(JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    public SnapshotReadiness check() {
        try {
            Instant lastCompleted = queryLastCompleted();
            boolean stale         = isStale(lastCompleted);
            boolean stuck         = hasStuckPipelines();
            int     dlqDepth      = queryDlqDepth();

            boolean ready = !stale && !stuck && dlqDepth < DLQ_WARN_THRESHOLD;
            String diagnosis = buildDiagnosis(stale, stuck, dlqDepth);

            if (!ready) {
                log.warn("SnapshotReadinessChecker: NOT READY stale={} stuck={} dlqDepth={}", stale, stuck, dlqDepth);
            }

            return new SnapshotReadiness(ready, stale, stuck, dlqDepth, lastCompleted, diagnosis);

        } catch (Exception e) {
            log.error("SnapshotReadinessChecker: check failed: {}", e.getMessage());
            return SnapshotReadiness.unavailable("check failed: " + e.getMessage());
        }
    }

    private Instant queryLastCompleted() {
        String sql = "SELECT MAX(completed_at) FROM pipeline_run WHERE status = 'COMPLETED'";
        java.sql.Timestamp ts = postgresJdbcTemplate.queryForObject(sql, java.sql.Timestamp.class);
        return ts != null ? ts.toInstant() : null;
    }

    private boolean isStale(Instant lastCompleted) {
        if (lastCompleted == null) return true;
        return lastCompleted.isBefore(Instant.now().minus(STALENESS_THRESHOLD_HOURS, ChronoUnit.HOURS));
    }

    private boolean hasStuckPipelines() {
        String sql = """
                SELECT COUNT(*)
                FROM pipeline_run
                WHERE status = 'IN_PROGRESS'
                  AND started_at < ?
                """;
        Instant stuckThreshold = Instant.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class,
                java.sql.Timestamp.from(stuckThreshold));
        return count != null && count > 0;
    }

    private int queryDlqDepth() {
        String sql = "SELECT COUNT(*) FROM dead_letter_queue WHERE resolved_at IS NULL";
        Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    private String buildDiagnosis(boolean stale, boolean stuck, int dlqDepth) {
        if (!stale && !stuck && dlqDepth < DLQ_WARN_THRESHOLD) return "OK";
        StringBuilder sb = new StringBuilder();
        if (stale)                             sb.append("STALE(>").append(STALENESS_THRESHOLD_HOURS).append("h) ");
        if (stuck)                             sb.append("STUCK_PIPELINES ");
        if (dlqDepth >= DLQ_WARN_THRESHOLD)    sb.append("DLQ_HIGH(").append(dlqDepth).append(") ");
        return sb.toString().strip();
    }
}
