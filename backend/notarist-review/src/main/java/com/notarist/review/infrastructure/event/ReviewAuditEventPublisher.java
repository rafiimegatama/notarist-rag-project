package com.notarist.review.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes audit events for OCR review operations, via the same cross-module
 * {@link AuditEventPayload} the document and case modules already emit. Lives in core's payload type
 * so notarist-review need not depend on notarist-audit.
 */
@Component
public class ReviewAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public ReviewAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishFieldReviewed(UUID reviewId, UUID documentId, UUID actorUserId, String actorRole,
                                     UUID tenantId, String fieldName, String decision,
                                     CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reviewId", reviewId.toString());
        detail.put("documentId", documentId.toString());
        detail.put("field", fieldName);
        detail.put("decision", decision);
        publish("OCR_FIELD_REVIEWED", reviewId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    public void publishStatusChanged(UUID reviewId, UUID documentId, UUID actorUserId, String actorRole,
                                     UUID tenantId, String fromStatus, String toStatus,
                                     CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reviewId", reviewId.toString());
        detail.put("documentId", documentId.toString());
        detail.put("from", fromStatus);
        detail.put("to", toStatus);
        publish("OCR_REVIEW_STATUS_CHANGED", reviewId, actorUserId, actorRole, tenantId,
                "UPDATE", "SUCCESS", correlationId, detail);
    }

    /** A refused read — a cross-tenant probe of a review the caller may not see. */
    public void publishAccessDenied(UUID documentId, UUID actorUserId, String actorRole, UUID tenantId,
                                    String reason, CorrelationId correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("documentId", documentId.toString());
        detail.put("reason", reason);
        eventPublisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "OCR_REVIEW", documentId.toString(),
                actorUserId, actorRole, tenantId,
                "READ", "FAILURE", null, correlationId.value(), detail));
    }

    private void publish(String eventType, UUID reviewId, UUID actorUserId, String actorRole,
                         UUID tenantId, String action, String outcome, CorrelationId correlationId,
                         Map<String, Object> detail) {
        eventPublisher.publishEvent(new AuditEventPayload(
                eventType, "OCR_REVIEW", reviewId.toString(),
                actorUserId, actorRole, tenantId,
                action, outcome, null, correlationId.value(), detail));
    }
}
