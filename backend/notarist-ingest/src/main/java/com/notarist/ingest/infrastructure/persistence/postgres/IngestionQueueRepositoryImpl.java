package com.notarist.ingest.infrastructure.persistence.postgres;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.application.port.out.IngestQueueRepository;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL implementation using SKIP LOCKED for concurrent safe stage dequeue.
 * Uses the ingestion_queue table (updated schema from V2 migration).
 */
@Repository
public class IngestionQueueRepositoryImpl implements IngestQueueRepository {

    private static final Logger log = LoggerFactory.getLogger(IngestionQueueRepositoryImpl.class);

    private static final String SQL_ENQUEUE = """
            INSERT INTO ingestion_queue
                (queue_job_id, ingestion_id, job_id, tenant_id, target_stage,
                 status, payload, attempt_count, scheduled_at, created_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?::jsonb, 0, ?, NOW())
            """;

    private static final String SQL_CLAIM_SKIP_LOCKED = """
            UPDATE ingestion_queue
            SET status = 'PROCESSING', locked_by = ?, locked_at = NOW()
            WHERE queue_job_id IN (
                SELECT queue_job_id
                FROM ingestion_queue
                WHERE target_stage = ?
                  AND status = 'PENDING'
                  AND scheduled_at <= NOW()
                ORDER BY scheduled_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            RETURNING queue_job_id, ingestion_id, job_id, tenant_id,
                      target_stage, payload, attempt_count, scheduled_at
            """;

    private static final String SQL_MARK_COMPLETED = """
            UPDATE ingestion_queue
            SET status = 'COMPLETED', completed_at = NOW(), locked_by = NULL, locked_at = NULL
            WHERE queue_job_id = ?
            """;

    // Marks the claimed row terminally FAILED (not back to PENDING). RetryPolicyService is the
    // single re-enqueue driver — it transitions the job from FAILED to its retry stage and inserts
    // a fresh PENDING row. Re-PENDING here would create a second, competing retry driver and reset
    // the attempt bookkeeping (F15). attempt_count mirrors the authoritative job.retryCount.
    private static final String SQL_MARK_FAILED = """
            UPDATE ingestion_queue
            SET status = 'FAILED',
                attempt_count = ?,
                next_retry_at = ?,
                error_detail = ?,
                locked_by = NULL,
                locked_at = NULL
            WHERE queue_job_id = ?
            """;

    private static final String SQL_MOVE_DLQ = """
            UPDATE ingestion_queue
            SET status = 'DLQ',
                dlq_reason = ?,
                locked_by = NULL,
                locked_at = NULL,
                completed_at = NOW()
            WHERE queue_job_id = ?
            """;

    private static final String SQL_COUNT_PENDING = """
            SELECT COUNT(*) FROM ingestion_queue
            WHERE tenant_id = ? AND status = 'PENDING'
            """;

    private final JdbcTemplate postgresJdbcTemplate;

    public IngestionQueueRepositoryImpl(
            @Qualifier("ingestJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @Override
    public void enqueue(IngestionId ingestionId, JobId jobId, UUID tenantId,
                        PipelineStatus targetStage, String payloadJson, Instant scheduledAt) {
        UUID queueJobId = UUID.randomUUID();
        postgresJdbcTemplate.update(SQL_ENQUEUE,
                queueJobId,
                ingestionId.value(),
                jobId.value(),
                tenantId,
                targetStage.name(),
                payloadJson != null ? payloadJson : "{}",
                Timestamp.from(scheduledAt));
        log.debug("Enqueued stage={} queueJobId={} ingestionId={}", targetStage, queueJobId, ingestionId);
    }

    /**
     * Claims up to {@code limit} pending records in a single atomic
     * UPDATE ... WHERE IN (SELECT ... FOR UPDATE SKIP LOCKED) ... RETURNING
     * statement. Atomicity within one statement is required because this
     * datasource runs with autocommit (no transaction manager exists for it) —
     * a separate SELECT-then-UPDATE would let two scheduler nodes claim the
     * same rows between the two statements.
     */
    @Override
    public List<QueueRecord> dequeueForProcessing(PipelineStatus targetStage, String workerId, int limit) {
        return postgresJdbcTemplate.query(
                SQL_CLAIM_SKIP_LOCKED,
                this::mapRow,
                workerId, targetStage.name(), limit);
    }

    @Override
    public void markCompleted(UUID queueJobId) {
        int updated = postgresJdbcTemplate.update(SQL_MARK_COMPLETED, queueJobId);
        if (updated == 0) {
            log.warn("markCompleted found no row for queueJobId={}", queueJobId);
        }
    }

    @Override
    public void markFailed(UUID queueJobId, String errorCode, int attemptCount, Instant nextRetryAt) {
        postgresJdbcTemplate.update(SQL_MARK_FAILED,
                attemptCount, Timestamp.from(nextRetryAt), errorCode, queueJobId);
    }

    @Override
    public void moveToDlq(UUID queueJobId, String reason) {
        postgresJdbcTemplate.update(SQL_MOVE_DLQ, reason, queueJobId);
        log.info("Queue entry moved to DLQ queueJobId={} reason={}", queueJobId, reason);
    }

    @Override
    public long countPending(UUID tenantId) {
        Long count = postgresJdbcTemplate.queryForObject(SQL_COUNT_PENDING, Long.class, tenantId);
        return count != null ? count : 0L;
    }

    private QueueRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new QueueRecord(
                UUID.fromString(rs.getString("queue_job_id")),
                IngestionId.of(UUID.fromString(rs.getString("ingestion_id"))),
                new JobId(UUID.fromString(rs.getString("job_id"))),
                UUID.fromString(rs.getString("tenant_id")),
                PipelineStatus.valueOf(rs.getString("target_stage")),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                rs.getTimestamp("scheduled_at").toInstant());
    }
}
