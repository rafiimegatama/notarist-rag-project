package com.notarist.kase.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Audit events for Bundle lifecycle operations, via the shared cross-module {@link AuditEventPayload}. */
@Component
public class BundleAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public BundleAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishBundleCreated(UUID bundleId, UUID caseId, UUID actorUserId, String actorRole,
                                     UUID tenantId, String bundleType, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bundleId", bundleId.toString());
        detail.put("caseId", caseId.toString());
        detail.put("bundleType", bundleType);
        emit("BUNDLE_CREATED", bundleId, actorUserId, actorRole, tenantId,
                "CREATE", "SUCCESS", correlationId, detail);
    }

    public void publishStatusChanged(UUID bundleId, UUID actorUserId, String actorRole, UUID tenantId,
                                     String from, String to, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bundleId", bundleId.toString());
        detail.put("from", from);
        detail.put("to", to);
        emit("BUNDLE_STATUS_CHANGED", bundleId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    public void publishAccessDenied(UUID bundleId, UUID actorUserId, String actorRole, UUID tenantId,
                                    String reason, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("bundleId", bundleId.toString());
        detail.put("reason", reason);
        eventPublisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "BUNDLE", bundleId.toString(),
                actorUserId, actorRole, tenantId,
                "READ", "FAILURE", null, correlationId.value(), detail));
    }

    private void emit(String eventType, UUID bundleId, UUID actorUserId, String actorRole,
                      UUID tenantId, String action, String outcome, CorrelationId correlationId,
                      Map<String, Object> detail) {
        eventPublisher.publishEvent(new AuditEventPayload(
                eventType, "BUNDLE", bundleId.toString(),
                actorUserId, actorRole, tenantId,
                action, outcome, null, correlationId.value(), detail));
    }
}
