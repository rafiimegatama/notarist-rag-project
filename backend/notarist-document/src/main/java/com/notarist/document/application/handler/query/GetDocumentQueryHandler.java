package com.notarist.document.application.handler.query;

import com.notarist.core.domain.exception.DocumentNotFoundException;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.document.api.response.DocumentLegalResponse;
import com.notarist.document.application.port.in.GetDocumentUseCase;
import com.notarist.document.application.port.out.DocumentLegalRepository;
import com.notarist.document.application.query.GetDocumentQuery;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.infrastructure.event.DocumentAuditEventPublisher;
import com.notarist.document.infrastructure.persistence.mapper.DocumentLegalMapper;
import org.springframework.stereotype.Service;

@Service
public class GetDocumentQueryHandler implements GetDocumentUseCase {

    private final DocumentLegalRepository documentRepository;
    private final DocumentLegalMapper mapper;
    private final DocumentAuditEventPublisher auditPublisher;

    public GetDocumentQueryHandler(
            DocumentLegalRepository documentRepository,
            DocumentLegalMapper mapper,
            DocumentAuditEventPublisher auditPublisher) {
        this.documentRepository = documentRepository;
        this.mapper = mapper;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public DocumentLegalResponse execute(DocumentId documentId, CallerContext caller) {
        return handle(new GetDocumentQuery(
                documentId,
                caller.userId(),
                caller.role(),
                caller.tenantId(),
                com.notarist.core.domain.valueobject.CorrelationId.generate()
        ));
    }

    public DocumentLegalResponse handle(GetDocumentQuery query) {
        DocumentLegal document = documentRepository.findById(query.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(
                        "DOCUMENT_NOT_FOUND",
                        "Document not found: " + query.documentId().value()));

        if (!tenantMatches(document, query)) {
            throw new DocumentNotFoundException(
                    "DOCUMENT_NOT_FOUND", "Document not found: " + query.documentId().value());
        }

        assertClearanceAllowed(query.actorRole(), document.getClassificationLevel());

        auditPublisher.publishDocumentAccess(
                query.documentId(), query.actorUserId(), query.actorRole(),
                query.tenantId(), query.correlationId());

        return mapper.toResponse(document);
    }

    private boolean tenantMatches(DocumentLegal document, GetDocumentQuery query) {
        return document.getTenantId().equals(query.tenantId());
    }

    private void assertClearanceAllowed(String actorRole, ClassificationLevel docLevel) {
        ClassificationLevel callerClearance = resolveClearance(actorRole);
        if (docLevel.exceeds(callerClearance)) {
            throw new UnauthorizedAccessException(
                    "DOCUMENT_INSUFFICIENT_CLEARANCE",
                    "Caller role " + actorRole + " does not have clearance for " + docLevel + " document");
        }
    }

    private ClassificationLevel resolveClearance(String roleName) {
        return switch (roleName) {
            case "ADMIN", "PIMPINAN" -> ClassificationLevel.STRICTLY_CONFIDENTIAL;
            case "NOTARIS", "PPAT_OFFICER" -> ClassificationLevel.CONFIDENTIAL;
            default -> ClassificationLevel.INTERNAL;
        };
    }
}
