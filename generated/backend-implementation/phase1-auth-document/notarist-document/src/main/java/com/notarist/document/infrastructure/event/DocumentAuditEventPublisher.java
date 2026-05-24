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
