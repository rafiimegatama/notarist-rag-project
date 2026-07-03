package com.notarist.document.application.handler.query;

import com.notarist.core.api.response.PageResponse;
import com.notarist.document.api.response.DocumentLegalResponse;
import com.notarist.document.application.port.out.DocumentLegalRepository;
import com.notarist.document.application.query.ListDocumentsQuery;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.infrastructure.event.DocumentAuditEventPublisher;
import com.notarist.document.infrastructure.persistence.mapper.DocumentLegalMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ListDocumentsQueryHandler {

    private final DocumentLegalRepository documentRepository;
    private final DocumentLegalMapper mapper;
    private final DocumentAuditEventPublisher auditPublisher;

    public ListDocumentsQueryHandler(
            DocumentLegalRepository documentRepository,
            DocumentLegalMapper mapper,
            DocumentAuditEventPublisher auditPublisher) {
        this.documentRepository = documentRepository;
        this.mapper = mapper;
        this.auditPublisher = auditPublisher;
    }

    public PageResponse<DocumentLegalResponse> handle(ListDocumentsQuery query) {
        DocumentLegalRepository.DocumentFilter filter = new DocumentLegalRepository.DocumentFilter(
                query.documentTypeFilter(),
                query.statusFilter(),
                query.callerMaxClearance()
        );

        List<DocumentLegal> documents = documentRepository.findByTenantId(
                query.tenantId(), filter, query.page(), query.size());
        long total = documentRepository.countByTenantId(query.tenantId(), filter);

        List<DocumentLegalResponse> responses = documents.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());

        auditPublisher.publishDocumentList(
                query.actorUserId(), query.actorRole(), query.tenantId(),
                responses.size(), query.correlationId());

        return PageResponse.of(responses, query.page(), query.size(), total);
    }
}
