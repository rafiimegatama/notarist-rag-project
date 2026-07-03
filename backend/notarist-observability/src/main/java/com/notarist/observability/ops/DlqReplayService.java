package com.notarist.observability.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replays items from the Dead Letter Queue (DLQ).
 *
 * DLQ items are ingestion jobs that have exceeded max retry_count or
 * failed at a non-retryable stage (e.g., PDF validation failure).
 *
 * Replay modes:
 *   FORCE_RETRY  — resets retry_count=0, returns to PENDING; bypasses non-retryable flag
 *   MARK_RESOLVED — marks as manually resolved (e.g., corrupt file discarded intentionally)
 *
 * Both modes require operatorId for audit. Replay is per-item or bulk-by-failure-stage.
 * Exponential backoff schedule is NOT applied to DLQ replays — operator chose to retry.
 */
@Component
public class DlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class);

    public enum ReplayMode { FORCE_RETRY, MARK_RESOLVED }

    public record DlqItem(
            String itemId,
            String documentId,
            String tenantId,
            String failureStage,
            int    retryCount,
            String failureReason
    ) {}

    public record DlqReplayResult(
            int          processedCount,
            List<String> processedIds,
            ReplayMode   mode,
            String       status
    ) {
        public static DlqReplayResult empty() {
            return new DlqReplayResult(0, List.of(), null, "NOTHING_IN_DLQ");
        }
        public static DlqReplayResult error(String m) {
            return new DlqReplayResult(0, List.of(), null, "ERROR: " + m);
        }
    }

    private final JdbcTemplate postgresJdbcTemplate;

    public DlqReplayService(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    public List<DlqItem> listPending(String tenantId, int limit) {
        String sql = """
                SELECT dlq_id::text, document_id::text, tenant_id::text,
                       failure_stage, retry_count, dead_letter_reason
                FROM dead_letter_queue
                WHERE resolved_at IS NULL
                  AND (? IS NULL OR tenant_id = ?::uuid)
                ORDER BY created_at ASC
                LIMIT ?
                """;
        return postgresJdbcTemplate.query(sql,
                (rs, i) -> new DlqItem(
                        rs.getString("dlq_id"),
                        rs.getString("document_id"),
                        rs.getString("tenant_id"),
                        rs.getString("failure_stage"),
                        rs.getInt("retry_count"),
                        rs.getString("dead_letter_reason")
                ),
                tenantId, tenantId, limit);
    }

    public DlqReplayResult replay(String dlqItemId, ReplayMode mode, String operatorId) {
        String traceId = UUID.randomUUID().toString();
        log.info("DlqReplayService: replay dlqItemId={} mode={} operatorId={} traceId={}",
                dlqItemId, mode, operatorId, traceId);

        try {
            return switch (mode) {
                case FORCE_RETRY   -> forceRetry(dlqItemId, operatorId, traceId);
                case MARK_RESOLVED -> markResolved(dlqItemId, operatorId, traceId);
            };
        } catch (Exception e) {
            log.error("DlqReplayService: replay failed dlqItemId={}: {}", dlqItemId, e.getMessage());
            return DlqReplayResult.error(e.getMessage());
        }
    }

    public DlqReplayResult replayByFailureStage(String failureStage, String tenantId,
                                                  ReplayMode mode, String operatorId) {
        String traceId = UUID.randomUUID().toString();
        log.info("DlqReplayService: bulk replay stage={} tenantId={} mode={} operatorId={} traceId={}",
                failureStage, tenantId, mode, operatorId, traceId);

        try {
            List<String> ids = postgresJdbcTemplate.queryForList(
                    """
                    SELECT dlq_id::text FROM dead_letter_queue
                    WHERE failure_stage = ? AND resolved_at IS NULL
                      AND (? IS NULL OR tenant_id = ?::uuid)
                    """, String.class, failureStage, tenantId, tenantId);

            if (ids.isEmpty()) return DlqReplayResult.empty();

            int processed = 0;
            for (String id : ids) {
                DlqReplayResult r = replay(id, mode, operatorId);
                if ("PROCESSED".equals(r.status())) processed++;
            }

            return new DlqReplayResult(processed, ids, mode, "BULK_PROCESSED");

        } catch (Exception e) {
            log.error("DlqReplayService: bulk replay failed stage={}: {}", failureStage, e.getMessage());
            return DlqReplayResult.error(e.getMessage());
        }
    }

    private DlqReplayResult forceRetry(String dlqItemId, String operatorId, String traceId) {
        String getSql = """
                SELECT document_id::text FROM dead_letter_queue WHERE dlq_id = ?::uuid
                """;
        Map<String, Object> row = postgresJdbcTemplate.queryForMap(getSql, dlqItemId);
        String documentId = (String) row.get("document_id");

        postgresJdbcTemplate.update("""
                UPDATE dead_letter_queue
                SET resolved_at = NOW(), resolved_by = ?, resolve_trace_id = ?, resolve_reason = 'FORCE_RETRY'
                WHERE dlq_id = ?::uuid
                """, operatorId, traceId, dlqItemId);

        postgresJdbcTemplate.update("""
                UPDATE pipeline_run
                SET status = 'PENDING', retry_count = 0, failure_stage = NULL, failure_reason = NULL,
                    replayed_by = ?, replayed_at = NOW(), replay_trace_id = ?
                WHERE document_id = ?::uuid AND status = 'FAILED'
                """, operatorId, traceId, documentId);

        log.info("DlqReplayService: FORCE_RETRY dlqItemId={} documentId={} traceId={}",
                dlqItemId, documentId, traceId);
        return new DlqReplayResult(1, List.of(dlqItemId), ReplayMode.FORCE_RETRY, "PROCESSED");
    }

    private DlqReplayResult markResolved(String dlqItemId, String operatorId, String traceId) {
        postgresJdbcTemplate.update("""
                UPDATE dead_letter_queue
                SET resolved_at = NOW(), resolved_by = ?, resolve_trace_id = ?, resolve_reason = 'MANUAL_RESOLVE'
                WHERE dlq_id = ?::uuid
                """, operatorId, traceId, dlqItemId);

        log.info("DlqReplayService: MARK_RESOLVED dlqItemId={} traceId={}", dlqItemId, traceId);
        return new DlqReplayResult(1, List.of(dlqItemId), ReplayMode.MARK_RESOLVED, "PROCESSED");
    }
}
