package com.notarist.document.application.port.out;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.domain.model.DocumentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentLegalRepository {
    Optional<DocumentLegal> findById(DocumentId documentId);
    Optional<DocumentLegal> findByChecksumAndTenantId(String checksumSha256, UUID tenantId);
    List<DocumentLegal> findByTenantId(UUID tenantId, DocumentFilter filter, int page, int size);
    long countByTenantId(UUID tenantId, DocumentFilter filter);
    void save(DocumentLegal document);
    void updateStatus(DocumentId documentId, DocumentStatus status);

    record DocumentFilter(
        JenisDokumen documentType,
        DocumentStatus status,
        ClassificationLevel maxClearance
    ) {}
}
