package com.notarist.verification.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes audit events for verification operations, via the same cross-module
 * {@link AuditEventPayload} the other modules already emit. Lives on core's payload type so
 * notarist-verification need not depend on notarist-audit.
 */
@Component
public class VerificationAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public VerificationAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishItemDecided(UUID verificationId, UUID bundleId, UUID actorUserId, String actorRole,
                                   UUID tenantId, String itemTitle, String decision,
                                   CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("verificationId", verificationId.toString());
        detail.put("bundleId", bundleId.toString());
        detail.put("item", itemTitle);
        detail.put("decision", decision);
        publish("VERIFICATION_ITEM_DECIDED", verificationId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    public void publishStatusChanged(UUID verificationId, UUID bundleId, UUID actorUserId, String actorRole,
                                     UUID tenantId, String fromStatus, String toStatus,
                                     CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("verificationId", verificationId.toString());
        detail.put("bundleId", bundleId.toString());
        detail.put("from", fromStatus);
        detail.put("to", toStatus);
        publish("VERIFICATION_STATUS_CHANGED", verificationId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    /** A refused read — a cross-tenant probe of a verification the caller may not see. */
    public void publishAccessDenied(UUID bundleId, UUID actorUserId, String actorRole, UUID tenantId,
                                    String reason, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bundleId", bundleId.toString());
        detail.put("reason", reason);
        eventPublisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "VERIFICATION", bundleId.toString(),
                actorUserId, actorRole, tenantId,
                "READ", "FAILURE", null, correlationId.value(), detail));
    }

    private void publish(String eventType, UUID verificationId, UUID actorUserId, String actorRole,
                         UUID tenantId, String action, String outcome, CorrelationId correlationId,
                         Map<String, Object> detail) {
        eventPublisher.publishEvent(new AuditEventPayload(
                eventType, "VERIFICATION", verificationId.toString(),
                actorUserId, actorRole, tenantId,
                action, outcome, null, correlationId.value(), detail));
    }
}
