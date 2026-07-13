package com.notarist.audit.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notarist.audit.application.port.out.AuditTrailRepository;
import com.notarist.audit.domain.model.AuditEntry;
import com.notarist.audit.domain.model.AuditEventType;
import com.notarist.audit.domain.model.AuditOutcome;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only audit trail persistence — PostgreSQL {@code audit_trail} (Flyway V7).
 *
 * DB choice (F9): PostgreSQL, not Oracle, deliberately deviating from the original
 * port javadoc ("Oracle NOTARIST_SEC.AUDIT_TRAIL"):
 *   1. Oracle carries the VPD row-level policies. A failed login (AUTH_LOGIN_FAILURE) or a
 *      SECURITY_ACCESS_DENIED event has, by definition, no established VPD identity — the
 *      write would be filtered or would error out. The events that matter most for
 *      compliance are exactly the ones Oracle is least able to accept.
 *   2. The Oracle tenant policy is fail-closed (V005). An audit row with a NULL tenant — exactly
 *      what a failed login produces — is unwritable under it unless the audit path is handed a
 *      system exemption, i.e. unless we widen the very hole F7 exists to close.
 *   3. PostgreSQL is already the operational/security store for this module's neighbours
 *      (session_token, ingestion_queue, dead_letter_queue) and is the target of the Flyway
 *      migrations this table ships in.
 * 7-year retention is unaffected by the engine choice.
 *
 * Uses the shared HikariCP-backed "postgresJdbcTemplate" bean from
 * notarist-infra PostgresConnectionConfig — same convention as
 * auth SessionTokenRepositoryImpl and ingest IngestionQueueRepositoryImpl.
 *
 * All columns are listed explicitly; no SELECT * (project rule 7/8).
 */
@Repository
public class AuditTrailRepositoryImpl implements AuditTrailRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailRepositoryImpl.class);

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String COLUMNS = """
            audit_id, correlation_id, trace_id, event_type, event_category,
            subject_type, subject_id, actor_user_id, actor_role, tenant_id,
            action, outcome, detail_json, ip_address, user_agent, created_at
            """;

    private static final String SQL_APPEND = """
            INSERT INTO audit_trail
                (audit_id, correlation_id, trace_id, event_type, event_category,
                 subject_type, subject_id, actor_user_id, actor_role, tenant_id,
                 action, outcome, detail_json, ip_address, user_agent, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_ID = """
            SELECT audit_id, correlation_id, trace_id, event_type, event_category,
                   subject_type, subject_id, actor_user_id, actor_role, tenant_id,
                   action, outcome, detail_json, ip_address, user_agent, created_at
            FROM audit_trail
            WHERE audit_id = ?
            """;

    private final JdbcTemplate postgresJdbcTemplate;

    public AuditTrailRepositoryImpl(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @Override
    public void append(AuditEntry entry) {
        postgresJdbcTemplate.update(
                SQL_APPEND,
                entry.getAuditId() != null ? entry.getAuditId() : UUID.randomUUID(),
                entry.getCorrelationId() != null ? entry.getCorrelationId().value() : null,
                entry.getTraceId() != null ? entry.getTraceId().value() : null,
                entry.getEventType().name(),
                entry.getEventType().getCategory(),
                entry.getSubjectType(),
                entry.getSubjectId(),
                entry.getActorUserId(),
                entry.getActorRole(),
                entry.getTenantId(),
                entry.getAction(),
                entry.getOutcome().name(),
                serializeDetail(entry.getDetailJson()),
                entry.getIpAddress(),
                entry.getUserAgent(),
                Timestamp.from(entry.getCreatedAt())
        );
    }

    @Override
    public Optional<AuditEntry> findById(UUID auditId) {
        return postgresJdbcTemplate.query(SQL_FIND_BY_ID, ENTRY_ROW_MAPPER, auditId)
                .stream()
                .findFirst();
    }

    @Override
    public List<AuditEntry> findByFilter(AuditFilter filter, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(filter, args);

        int safeSize = size <= 0 ? 20 : Math.min(size, 500);
        int safePage = Math.max(page, 0);
        args.add(safeSize);
        args.add(safePage * (long) safeSize);

        String sql = "SELECT " + COLUMNS.strip()
                + " FROM audit_trail" + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        return postgresJdbcTemplate.query(sql, ENTRY_ROW_MAPPER, args.toArray());
    }

    @Override
    public long countByFilter(AuditFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(filter, args);
        Long count = postgresJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_trail" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /** Builds a parameterised WHERE clause; every predicate is bound, never interpolated. */
    private String buildWhere(AuditFilter filter, List<Object> args) {
        if (filter == null) return "";
        StringBuilder sb = new StringBuilder();
        if (filter.tenantId() != null)      { sb.append(" AND tenant_id = ?");      args.add(filter.tenantId()); }
        if (filter.eventCategory() != null) { sb.append(" AND event_category = ?"); args.add(filter.eventCategory()); }
        if (filter.eventType() != null)     { sb.append(" AND event_type = ?");     args.add(filter.eventType().name()); }
        if (filter.actorUserId() != null)   { sb.append(" AND actor_user_id = ?");  args.add(filter.actorUserId()); }
        if (filter.subjectId() != null)     { sb.append(" AND subject_id = ?");     args.add(filter.subjectId()); }
        if (filter.outcome() != null)       { sb.append(" AND outcome = ?");        args.add(filter.outcome().name()); }
        if (filter.dateFrom() != null)      { sb.append(" AND created_at >= ?");    args.add(Timestamp.from(filter.dateFrom())); }
        if (filter.dateTo() != null)        { sb.append(" AND created_at <= ?");    args.add(Timestamp.from(filter.dateTo())); }

        if (sb.length() == 0) return "";
        return " WHERE" + sb.substring(4);  // strip the leading " AND"
    }

    private static String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return "{}";
        try {
            return JSON.writeValueAsString(detail);
        } catch (Exception e) {
            // Never fail an audit append over an unserialisable detail map — keep the event,
            // record why the payload was lost.
            log.warn("Audit detail_json not serialisable, storing error marker: {}", e.getMessage());
            return "{\"detailSerializationError\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserializeDetail(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static final RowMapper<AuditEntry> ENTRY_ROW_MAPPER = (rs, rowNum) -> {
        String correlationId = rs.getString("correlation_id");
        String traceId = rs.getString("trace_id");
        String actorUserId = rs.getString("actor_user_id");
        String tenantId = rs.getString("tenant_id");
        Timestamp createdAt = rs.getTimestamp("created_at");

        return new AuditEntry(
                UUID.fromString(rs.getString("audit_id")),
                correlationId == null || correlationId.isBlank() ? null : CorrelationId.of(correlationId),
                traceId == null || traceId.isBlank() ? null : TraceId.of(traceId),
                AuditEventType.resolve(rs.getString("event_type")),
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                actorUserId == null ? null : UUID.fromString(actorUserId),
                rs.getString("actor_role"),
                tenantId == null ? null : UUID.fromString(tenantId),
                rs.getString("action"),
                AuditOutcome.valueOf(rs.getString("outcome")),
                deserializeDetail(rs.getString("detail_json")),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                createdAt == null ? Instant.now() : createdAt.toInstant()
        );
    };
}
