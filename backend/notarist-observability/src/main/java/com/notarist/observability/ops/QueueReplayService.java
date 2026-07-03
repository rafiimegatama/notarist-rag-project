package com.notarist.observability.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Replays stuck or halted items from active processing queues.
 *
 * Use cases:
 *   - Pipeline run stuck in IN_PROGRESS after node restart
 *   - Embedding job not progressing due to transient embedding service failure
 *   - OCR job left IN_PROGRESS after timeout without state update
 *
 * Replay strategy: reset status to PENDING so the ingestion worker picks it up again.
 * Does NOT increment retry_count — this is an operator-initiated recovery, not a system retry.
 *
 * All replay operations are logged with operator traceId for audit.
 */
@Component
public class QueueReplayService {

    private static final Logger log = LoggerFactory.getLogger(QueueReplayService.class);

    public record ReplayResult(int replayedCount, List<String> replayedIds, String status) {
        public static ReplayResult empty()        { return new ReplayResult(0, List.of(), "NOTHING_TO_REPLAY"); }
        public static ReplayResult error(String m){ return new ReplayResult(0, List.of(), "ERROR: " + m); }
    }

    private final JdbcTemplate postgresJdbcTemplate;

    public QueueReplayService(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    /**
     * Replays all pipeline runs stuck in IN_PROGRESS for the given tenant.
     *
     * @param tenantId    tenant scope (null = all tenants — admin only)
     * @param operatorId  operator who triggered replay (for audit log)
     * @return ReplayResult with count and IDs of reset runs
     */
    public ReplayResult replayStuckPipelineRuns(String tenantId, String operatorId) {
        String traceId = UUID.randomUUID().toString();
        log.info("QueueReplayService: replay triggered operatorId={} tenantId={} traceId={}",
                operatorId, tenantId != null ? tenantId : "ALL", traceId);

        try {
            List<String> stuckIds = findStuckPipelineRuns(tenantId);
            if (stuckIds.isEmpty()) return ReplayResult.empty();

            int updated = resetPipelineRunsToRetry(stuckIds, operatorId, traceId);

            log.info("QueueReplayService: replayed count={} traceId={}", updated, traceId);
            return new ReplayResult(updated, stuckIds, "REPLAYED");

        } catch (Exception e) {
            log.error("QueueReplayService: replay failed traceId={}: {}", traceId, e.getMessage());
            return ReplayResult.error(e.getMessage());
        }
    }

    /**
     * Replays specific pipeline run by ID (operator selects from dashboard).
     */
    public ReplayResult replayPipelineRun(UUID runId, String operatorId) {
        String traceId = UUID.randomUUID().toString();
        log.info("QueueReplayService: single replay runId={} operatorId={} traceId={}",
                runId, operatorId, traceId);

        try {
            String sql = """
                    UPDATE pipeline_run
                    SET status = 'PENDING',
                        failure_reason = NULL,
                        replayed_by = ?,
                        replayed_at = NOW(),
                        replay_trace_id = ?
                    WHERE run_id = ?::uuid
                      AND status IN ('IN_PROGRESS', 'FAILED')
                    """;
            int updated = postgresJdbcTemplate.update(sql, operatorId, traceId, runId.toString());
            return updated > 0
                    ? new ReplayResult(1, List.of(runId.toString()), "REPLAYED")
                    : new ReplayResult(0, List.of(), "NOT_ELIGIBLE");
        } catch (Exception e) {
            log.error("QueueReplayService: single replay failed runId={}: {}", runId, e.getMessage());
            return ReplayResult.error(e.getMessage());
        }
    }

    private List<String> findStuckPipelineRuns(String tenantId) {
        if (tenantId != null) {
            String sql = """
                    SELECT run_id::text
                    FROM pipeline_run
                    WHERE status = 'IN_PROGRESS'
                      AND tenant_id = ?::uuid
                      AND started_at < NOW() - INTERVAL '60 minutes'
                    """;
            return postgresJdbcTemplate.queryForList(sql, String.class, tenantId);
        } else {
            String sql = """
                    SELECT run_id::text
                    FROM pipeline_run
                    WHERE status = 'IN_PROGRESS'
                      AND started_at < NOW() - INTERVAL '60 minutes'
                    """;
            return postgresJdbcTemplate.queryForList(sql, String.class);
        }
    }

    private int resetPipelineRunsToRetry(List<String> runIds, String operatorId, String traceId) {
        String sql = """
                UPDATE pipeline_run
                SET status = 'PENDING',
                    failure_reason = NULL,
                    replayed_by = ?,
                    replayed_at = NOW(),
                    replay_trace_id = ?
                WHERE run_id::text = ANY(?)
                """;
        return postgresJdbcTemplate.update(sql, operatorId, traceId,
                postgresJdbcTemplate.getDataSource() != null
                        ? runIds.toArray(String[]::new)
                        : new String[0]);
    }
}
