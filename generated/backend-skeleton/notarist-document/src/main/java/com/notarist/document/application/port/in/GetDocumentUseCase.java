package com.notarist.document.application.port.in;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.document.api.response.DocumentLegalResponse;

public interface GetDocumentUseCase {
    DocumentLegalResponse execute(DocumentId documentId, CallerContext caller);

    record CallerContext(java.util.UUID userId, String role, java.util.UUID tenantId) {}
}
