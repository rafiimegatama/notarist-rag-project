package com.notarist.document.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Publishes audit events for document lifecycle operations via Spring ApplicationEventPublisher. */
@Component
public class DocumentAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public DocumentAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishDocumentAccess(
            DocumentId documentId,
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            CorrelationId correlationId) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "DOCUMENT_ACCESS", "DOCUMENT", documentId.value().toString(),
                actorUserId, actorRole, tenantId,
                "READ", "SUCCESS", null, correlationId.value(),
                Map.of("documentId", documentId.value().toString())
        ));
    }

    public void publishDocumentList(
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            int resultCount,
            CorrelationId correlationId) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "DOCUMENT_LIST", "DOCUMENT", "ALL",
                actorUserId, actorRole, tenantId,
                "LIST", "SUCCESS", null, correlationId.value(),
                Map.of("resultCount", resultCount)
        ));
    }

    /**
     * A refused attempt to reach a document — a cross-tenant request, or a caller whose clearance
     * is below the document's classification.
     *
     * <p>Previously these paths threw and left no trace: the audit trail recorded who successfully
     * read a document but not who was turned away from one, which is precisely the record a
     * notary/PPAT compliance review asks for. SECURITY events are fail-closed
     * ({@link com.notarist.audit.domain.model.AuditEventType#isFailClosed()}), so a denial that
     * cannot be written does not quietly disappear either.
     *
     * <p>{@code tenantId} is the CALLER's tenant, not the document's — attributing the event to the
     * target's tenant would file a cross-tenant probe under the tenant that was probed.
     */
    public void publishAccessDenied(
            DocumentId documentId,
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            String reason,
            CorrelationId correlationId) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "SECURITY_ACCESS_DENIED", "DOCUMENT", documentId.value().toString(),
                actorUserId, actorRole, tenantId,
                "READ", "FAILURE", null, correlationId.value(),
                Map.of("documentId", documentId.value().toString(), "reason", reason)
        ));
    }

    public void publishDocumentUpload(
            DocumentId documentId,
            UUID actorUserId,
            String actorRole,
            UUID tenantId,
            String documentTitle,
            CorrelationId correlationId) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "DOCUMENT_UPLOAD", "DOCUMENT", documentId.value().toString(),
                actorUserId, actorRole, tenantId,
                "UPLOAD", "SUCCESS", null, correlationId.value(),
                Map.of("documentId", documentId.value().toString(), "title", documentTitle)
        ));
    }
}
