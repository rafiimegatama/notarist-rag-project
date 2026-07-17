package com.notarist.audit.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain entity for an audit trail entry.
 * Append-only — no update or delete permitted.
 * Persisted to the PostgreSQL `audit_trail` table (Flyway V7) — see AuditTrailRepository.
 * Retention: 7 years per legal document domain requirement.
 */
public class AuditEntry {

    private final UUID auditId;
    private final CorrelationId correlationId;
    private final TraceId traceId;
    private final AuditEventType eventType;
    private final String subjectType;
    private final String subjectId;
    private final UUID actorUserId;
    private final String actorRole;
    private final UUID tenantId;
    private final String action;
    private final AuditOutcome outcome;
    private final Map<String, Object> detailJson;
    private final String ipAddress;
    private final String userAgent;
    private final Instant createdAt;

    public AuditEntry(
            UUID auditId,
            CorrelationId correlationId,
            TraceId traceId,
            AuditEventType eventType,
            String subjectType,
            String subjectId,
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            String action,
            AuditOutcome outcome,
            Map<String, Object> detailJson,
            String ipAddress,
            String userAgent) {
        this(auditId, correlationId, traceId, eventType, subjectType, subjectId, actorUserId,
                actorRole, tenantId, action, outcome, detailJson, ipAddress, userAgent, Instant.now());
    }

    /**
     * Rehydration constructor — used only by the persistence adapter when reading an
     * existing row back. Preserves the stored created_at instead of stamping "now",
     * which the append-time constructor above does.
     */
    public AuditEntry(
            UUID auditId,
            CorrelationId correlationId,
            TraceId traceId,
            AuditEventType eventType,
            String subjectType,
            String subjectId,
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            String action,
            AuditOutcome outcome,
            Map<String, Object> detailJson,
            String ipAddress,
            String userAgent,
            Instant createdAt) {
        this.auditId = auditId;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.eventType = eventType;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.tenantId = tenantId;
        this.action = action;
        this.outcome = outcome;
        this.detailJson = detailJson == null ? Map.of() : Map.copyOf(detailJson);
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID getAuditId() { return auditId; }
    public CorrelationId getCorrelationId() { return correlationId; }
    public TraceId getTraceId() { return traceId; }
    public AuditEventType getEventType() { return eventType; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorRole() { return actorRole; }
    public UUID getTenantId() { return tenantId; }
    public String getAction() { return action; }
    public AuditOutcome getOutcome() { return outcome; }
    public Map<String, Object> getDetailJson() { return detailJson; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
