package com.notarist.audit.infrastructure.event;

import com.notarist.audit.application.port.in.RecordAuditEventUseCase;
import com.notarist.audit.domain.model.AuditEntry;
import com.notarist.audit.domain.model.AuditEventType;
import com.notarist.audit.domain.model.AuditOutcome;
import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@link AuditEventPayload} from the Spring application event bus and persists it.
 * Before F9 remediation no listener existed at all: every login, logout, token refresh,
 * document access and ingestion transition was published into the bus and dropped.
 *
 * <h3>Why plain {@code @EventListener} and not {@code @TransactionalEventListener(AFTER_COMMIT)}</h3>
 * <ol>
 *   <li><b>AFTER_COMMIT would lose the most important events.</b> AUTH_LOGIN_FAILURE (and any
 *       SECURITY_ACCESS_DENIED) is published on a path that immediately throws
 *       UnauthorizedAccessException. There is no business transaction that commits — so an
 *       AFTER_COMMIT listener would never fire, and a brute-force attempt would leave no trace.
 *       That is precisely the record a notary/PPAT office is obliged to keep.</li>
 *   <li><b>The auth handlers are not transactional at all.</b> They write via JdbcTemplate on an
 *       autocommit datasource, so no transaction is bound to the thread. A phase-bound listener
 *       is silently skipped when no transaction is active (unless fallbackExecution is set), which
 *       would reintroduce the exact silent-drop bug being fixed.</li>
 *   <li><b>There is nothing to roll back into.</b> The audit write targets the PostgreSQL audit
 *       store, a different datasource from the Oracle business tables and not enlisted in any
 *       shared/XA transaction. So the usual AFTER_COMMIT argument ("don't persist an audit record
 *       for work that later rolls back") does not hold here — the audit row commits independently
 *       either way. Writing synchronously, in-line, is the option that actually preserves events.</li>
 * </ol>
 *
 * <h3>Failure semantics</h3>
 * Fail-closed for AUTH / SECURITY / DOCUMENT events (see {@link AuditEventType#isFailClosed()}):
 * the exception propagates out of {@code publishEvent(...)} back into the caller, so an operation
 * that cannot be audited does not succeed. That matches the RecordAuditEventUseCase contract
 * ("Failure to write = business operation failure"). Fail-open (log at ERROR, swallow) for
 * INGEST / SEARCH / ASSISTANT events, so that an audit-store blip degrades telemetry rather than
 * dead-lettering documents in a pipeline that already has its own retry/DLQ machinery.
 *
 * Being synchronous, this listener runs on the caller's thread and adds one indexed INSERT to the
 * request. If that cost ever matters, the correct next step is a durable outbox — NOT @Async,
 * which would re-open the same in-VM drop window on shutdown.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public AuditEventListener(RecordAuditEventUseCase recordAuditEventUseCase) {
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @EventListener
    public void onAuditEvent(AuditEventPayload payload) {
        AuditEntry entry = toEntry(payload);
        try {
            recordAuditEventUseCase.execute(entry);
        } catch (RuntimeException e) {
            if (entry.getEventType().isFailClosed()) {
                log.error("Audit write FAILED (fail-closed) for eventType={} actor={} — failing the business operation",
                        payload.eventType(), payload.actorUserId(), e);
                throw e;
            }
            log.error("Audit write FAILED (fail-open) for eventType={} actor={} — event lost, business operation continues",
                    payload.eventType(), payload.actorUserId(), e);
        }
    }

    /**
     * Maps the cross-module payload to the audit domain entity.
     * Package-private and side-effect free so it can be unit tested without Spring or a DB.
     */
    static AuditEntry toEntry(AuditEventPayload payload) {
        AuditEventType eventType = AuditEventType.resolve(payload.eventType());

        Map<String, Object> detail = new LinkedHashMap<>(payload.detail());
        if (eventType == AuditEventType.UNMAPPED) {
            // Never drop an unregistered event type — keep the publisher's raw string.
            detail.put("sourceEventType", payload.eventType());
        }

        String correlationId = payload.correlationId();

        return new AuditEntry(
                UUID.randomUUID(),
                correlationId == null || correlationId.isBlank() ? null : CorrelationId.of(correlationId),
                null,  // traceId: not carried on AuditEventPayload today
                eventType,
                payload.subjectType(),
                payload.subjectId(),
                payload.actorUserId(),
                payload.actorRole(),
                payload.tenantId(),
                payload.action(),
                resolveOutcome(payload.outcome()),
                detail,
                payload.ipAddress(),
                null   // userAgent: not carried on AuditEventPayload today
        );
    }

    /** Tolerant outcome parsing — an unrecognised outcome must not discard the event. */
    private static AuditOutcome resolveOutcome(String raw) {
        if (raw == null || raw.isBlank()) return AuditOutcome.PARTIAL;
        try {
            return AuditOutcome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognised audit outcome '{}', recording as PARTIAL", raw);
            return AuditOutcome.PARTIAL;
        }
    }
}
