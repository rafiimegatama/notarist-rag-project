package com.notarist.document.infrastructure.persistence.mapper;

import com.notarist.core.domain.valueobject.*;
import com.notarist.document.api.response.DocumentLegalResponse;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.domain.model.DocumentStatus;
import com.notarist.document.infrastructure.persistence.oracle.DocumentLegalJpaEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DocumentLegalMapper {

    public DocumentLegal toDomain(DocumentLegalJpaEntity e) {
        DocumentLegal doc = new DocumentLegal(
                new DocumentId(UUID.fromString(e.getDocumentId())),
                e.getDocumentTitle(),
                JenisDokumen.valueOf(e.getDocumentType()),
                e.getJenisAkta() != null ? JenisAkta.valueOf(e.getJenisAkta()) : null,
                e.getNomorAkta() != null ? new NomorAkta(e.getNomorAkta()) : null,
                e.getTanggalAkta(),
                ClassificationLevel.valueOf(e.getClassificationLevel()),
                e.getMinioObjectKey(),
                e.getChecksumSha256(),
                e.getFileSizeBytes(),
                e.getMimeType(),
                e.getNotarisId() != null ? UUID.fromString(e.getNotarisId()) : null,
                UUID.fromString(e.getTenantId()),
                UUID.fromString(e.getUploadedBy())
        );
        doc.transitionStatus(DocumentStatus.valueOf(e.getStatus()));
        doc.setPageCount(e.getPageCount());
        if (e.getIndexedAt() != null) {
            doc.markIndexed(e.getIndexedAt());
        }
        return doc;
    }

    public DocumentLegalJpaEntity toEntity(DocumentLegal d) {
        return new DocumentLegalJpaEntity(
                d.getDocumentId().value().toString(),
                d.getDocumentTitle(),
                d.getDocumentType().name(),
                d.getJenisAkta() != null ? d.getJenisAkta().name() : null,
                d.getNomorAkta() != null ? d.getNomorAkta().value() : null,
                d.getTanggalAkta(),
                d.getClassificationLevel().name(),
                d.getStatus().name(),
                d.getMinioObjectKey(),
                d.getChecksumSha256(),
                d.getFileSizeBytes(),
                d.getMimeType(),
                d.getNotarisId() != null ? d.getNotarisId().toString() : null,
                d.getTenantId().toString(),
                d.getUploadedBy().toString(),
                d.getPageCount(),
                d.getVersionNumber(),
                d.getCreatedAt(),
                d.getIndexedAt()
        );
    }

    public DocumentLegalResponse toResponse(DocumentLegal d) {
        return new DocumentLegalResponse(
                d.getDocumentId().value(),
                d.getDocumentTitle(),
                d.getDocumentType().name(),
                d.getJenisAkta() != null ? d.getJenisAkta().name() : null,
                d.getNomorAkta() != null ? d.getNomorAkta().value() : null,
                d.getTanggalAkta() != null ? d.getTanggalAkta().toString() : null,
                d.getClassificationLevel().name(),
                d.getStatus().name(),
                d.getPageCount(),
                d.getFileSizeBytes(),
                d.getMimeType(),
                d.getNotarisId(),
                null,
                d.getVersionNumber(),
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
                d.getIndexedAt() != null ? d.getIndexedAt().toString() : null
        );
    }
}
