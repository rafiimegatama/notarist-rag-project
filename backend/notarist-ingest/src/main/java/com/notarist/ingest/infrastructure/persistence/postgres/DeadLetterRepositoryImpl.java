package com.notarist.ingest.infrastructure.persistence.postgres;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.application.port.out.DeadLetterRepository;
import com.notarist.ingest.domain.model.DeadLetterEntry;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only DLQ persistence in PostgreSQL. No updates — only inserts and reads. */
@Repository
public class DeadLetterRepositoryImpl implements DeadLetterRepository {

    private static final String SQL_INSERT = """
            INSERT INTO dead_letter_queue
                (dlq_id, ingestion_id, job_id, failure_stage, retry_count,
                 last_error_code, last_error_hash, next_retry_at,
                 dead_letter_reason, tenant_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_TENANT = """
            SELECT dlq_id, ingestion_id, job_id, failure_stage, retry_count,
                   last_error_code, last_error_hash, next_retry_at,
                   dead_letter_reason, tenant_id, created_at
            FROM dead_letter_queue
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String SQL_FIND_BY_INGESTION_ID = """
            SELECT dlq_id, ingestion_id, job_id, failure_stage, retry_count,
                   last_error_code, last_error_hash, next_retry_at,
                   dead_letter_reason, tenant_id, created_at
            FROM dead_letter_queue
            WHERE ingestion_id = ?
            ORDER BY created_at DESC
            """;

    private final JdbcTemplate postgresJdbcTemplate;

    public DeadLetterRepositoryImpl(
            @Qualifier("ingestJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @Override
    public void save(DeadLetterEntry entry) {
        postgresJdbcTemplate.update(SQL_INSERT,
                UUID.randomUUID(),
                entry.ingestionId().value(),
                entry.jobId().value(),
                entry.failureStage().name(),
                entry.retryCount(),
                entry.lastErrorCode(),
                entry.lastErrorHash(),
                entry.nextRetryAt() != null ? Timestamp.from(entry.nextRetryAt()) : null,
                entry.deadLetterReason(),
                entry.tenantId(),
                Timestamp.from(entry.createdAt()));
    }

    @Override
    public List<DeadLetterEntry> findByTenantId(UUID tenantId, int limit) {
        return postgresJdbcTemplate.query(SQL_FIND_BY_TENANT, this::mapRow, tenantId, limit);
    }

    @Override
    public List<DeadLetterEntry> findByIngestionId(IngestionId ingestionId) {
        return postgresJdbcTemplate.query(
                SQL_FIND_BY_INGESTION_ID, this::mapRow, ingestionId.value());
    }

    private DeadLetterEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp nextRetryTs = rs.getTimestamp("next_retry_at");
        return new DeadLetterEntry(
                IngestionId.of(UUID.fromString(rs.getString("ingestion_id"))),
                new JobId(UUID.fromString(rs.getString("job_id"))),
                UUID.fromString(rs.getString("tenant_id")),
                PipelineStatus.valueOf(rs.getString("failure_stage")),
                rs.getInt("retry_count"),
                rs.getString("last_error_code"),
                rs.getString("last_error_hash"),
                nextRetryTs != null ? nextRetryTs.toInstant() : null,
                rs.getString("dead_letter_reason"),
                rs.getTimestamp("created_at").toInstant());
    }

}
