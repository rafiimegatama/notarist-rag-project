package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes audit events for Case lifecycle operations, via the same cross-module
 * {@link AuditEventPayload} the document and auth modules already emit. Kept in core so notarist-case
 * need not depend on notarist-audit.
 */
@Component
public class CaseAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public CaseAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishCaseCreated(UUID caseId, UUID actorUserId, String actorRole, UUID tenantId,
                                   String caseNumber, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", caseId.toString());
        detail.put("caseNumber", caseNumber);
        publish("CASE_CREATED", caseId, actorUserId, actorRole, tenantId,
                "CREATE", "SUCCESS", correlationId, detail);
    }

    public void publishStatusChanged(UUID caseId, UUID actorUserId, String actorRole, UUID tenantId,
                                     String fromState, String toState, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", caseId.toString());
        detail.put("from", fromState);
        detail.put("to", toState);
        publish("CASE_STATUS_CHANGED", caseId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    /** A refused read — a cross-tenant probe of a case the caller may not see. */
    public void publishAccessDenied(UUID caseId, UUID actorUserId, String actorRole, UUID tenantId,
                                    String reason, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", caseId.toString());
        detail.put("reason", reason);
        eventPublisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "CASE", caseId.toString(),
                actorUserId, actorRole, tenantId,
                "READ", "FAILURE", null, correlationId.value(), detail));
    }

    private void publish(String eventType, UUID caseId, UUID actorUserId, String actorRole,
                         UUID tenantId, String action, String outcome, CorrelationId correlationId,
                         Map<String, Object> detail) {
        eventPublisher.publishEvent(new AuditEventPayload(
                eventType, "CASE", caseId.toString(),
                actorUserId, actorRole, tenantId,
                action, outcome, null, correlationId.value(), detail));
    }
}
