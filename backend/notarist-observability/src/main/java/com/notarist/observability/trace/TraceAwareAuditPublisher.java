package com.notarist.observability.trace;

import com.notarist.observability.audit.AuditCorrelationService;
import com.notarist.observability.log.LogSanitizer;
import com.notarist.observability.log.StructuredLogContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes audit events with full trace context.
 *
 * Every audit event includes:
 *   - correlation_id from current MDC (must be set by TracePropagationService)
 *   - trace_id (operation-level identifier)
 *   - event_type (typed enum, not freeform string)
 *   - actor (userId + tenantId)
 *   - resource (documentId or sessionId)
 *   - outcome (SUCCESS / FAILURE / DEGRADED)
 *   - sanitized payload (via LogSanitizer — no PII, no raw text)
 *
 * Audit sink is structured SLF4J to a dedicated "notarist.audit" logger.
 * In production, route this logger to a separate log stream / audit store.
 *
 * All emit methods are non-blocking and non-throwing — audit must never break the caller.
 */
@Component
public class TraceAwareAuditPublisher {

    private static final Logger auditLog = LoggerFactory.getLogger("notarist.audit");

    public enum AuditEventType {
        DOCUMENT_UPLOADED,
        DOCUMENT_INGESTED,
        DOCUMENT_FAILED,
        SEARCH_EXECUTED,
        ASSISTANT_QUERIED,
        ASSISTANT_RESPONSE_GENERATED,
        REINDEX_TRIGGERED,
        DLQ_REPLAYED,
        QUEUE_REPLAYED,
        MIGRATION_VALIDATED,
        CIRCUIT_OPENED,
        CIRCUIT_CLOSED,
        DEGRADATION_CHANGED
    }

    public enum AuditOutcome { SUCCESS, FAILURE, DEGRADED, REJECTED }

    public record AuditEvent(
            String         eventId,
            AuditEventType type,
            String         correlationId,
            String         traceId,
            String         userId,
            String         tenantId,
            String         resourceId,
            AuditOutcome   outcome,
            String         detail,
            Instant        occurredAt
    ) {}

    private final LogSanitizer             sanitizer;
    private final AuditCorrelationService  correlationService;

    public TraceAwareAuditPublisher(LogSanitizer sanitizer,
                                     AuditCorrelationService correlationService) {
        this.sanitizer          = sanitizer;
        this.correlationService = correlationService;
    }

    public void publish(AuditEventType type, AuditOutcome outcome, String resourceId, String detail) {
        try {
            String correlationId = mdc(StructuredLogContract.KEY_CORRELATION_ID);
            String traceId       = mdc(StructuredLogContract.KEY_TRACE_ID);
            String userId        = mdc(StructuredLogContract.KEY_USER_ID);
            String tenantId      = mdc(StructuredLogContract.KEY_TENANT_ID);

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    type,
                    correlationId,
                    traceId,
                    userId,
                    tenantId,
                    resourceId,
                    outcome,
                    sanitizer.sanitize(detail),
                    Instant.now()
            );

            emitAuditLog(event);

        } catch (Exception e) {
            auditLog.error("TraceAwareAuditPublisher: failed to publish event type={}: {}",
                    type, e.getMessage());
        }
    }

    public void publishWithCorrelation(AuditEventType type, AuditOutcome outcome,
                                        String correlationId, String resourceId, String detail) {
        try {
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    type,
                    correlationId,
                    mdc(StructuredLogContract.KEY_TRACE_ID),
                    mdc(StructuredLogContract.KEY_USER_ID),
                    mdc(StructuredLogContract.KEY_TENANT_ID),
                    resourceId,
                    outcome,
                    sanitizer.sanitize(detail),
                    Instant.now()
            );
            emitAuditLog(event);
        } catch (Exception e) {
            auditLog.error("TraceAwareAuditPublisher: publish failed correlationId={}: {}", correlationId, e.getMessage());
        }
    }

    private void emitAuditLog(AuditEvent event) {
        auditLog.info(
                "audit=NOTARIST_EVENT eventId={} type={} correlationId={} traceId={} " +
                "userId={} tenantId={} resourceId={} outcome={} detail={} occurredAt={}",
                event.eventId(),
                event.type().name(),
                event.correlationId(),
                event.traceId(),
                event.userId(),
                event.tenantId(),
                event.resourceId() != null ? event.resourceId() : "none",
                event.outcome().name(),
                event.detail() != null ? event.detail() : "none",
                event.occurredAt()
        );
    }

    private static String mdc(String key) {
        String v = MDC.get(key);
        return v != null ? v : "none";
    }
}
