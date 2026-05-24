package com.notarist.document.domain.model;

import com.notarist.core.domain.valueobject.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for legal documents in NOTARIST RAG Platform.
 * Immutable after INDEXED status — no field modification post-indexing.
 */
public class DocumentLegal {

    private final DocumentId documentId;
    private final String documentTitle;
    private final JenisDokumen documentType;
    private final JenisAkta jenisAkta;
    private final NomorAkta nomorAkta;
    private final LocalDate tanggalAkta;
    private final ClassificationLevel classificationLevel;
    private DocumentStatus status;
    private final String minioObjectKey;
    private final String checksumSha256;
    private final Long fileSizeBytes;
    private final String mimeType;
    private final UUID notarisId;
    private final UUID tenantId;
    private final UUID uploadedBy;
    private Integer pageCount;
    private int versionNumber;
    private final Instant createdAt;
    private Instant indexedAt;

    public DocumentLegal(
            DocumentId documentId,
            String documentTitle,
            JenisDokumen documentType,
            JenisAkta jenisAkta,
            NomorAkta nomorAkta,
            LocalDate tanggalAkta,
            ClassificationLevel classificationLevel,
            String minioObjectKey,
            String checksumSha256,
            Long fileSizeBytes,
            String mimeType,
            UUID notarisId,
            UUID tenantId,
            UUID uploadedBy) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentType = documentType;
        this.jenisAkta = jenisAkta;
        this.nomorAkta = nomorAkta;
        this.tanggalAkta = tanggalAkta;
        this.classificationLevel = classificationLevel;
        this.status = DocumentStatus.UPLOADED;
        this.minioObjectKey = minioObjectKey;
        this.checksumSha256 = checksumSha256;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.notarisId = notarisId;
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.versionNumber = 1;
        this.createdAt = Instant.now();
    }

    // TODO (STEP 8B): implement domain invariant — no status change after INDEXED

    public void transitionStatus(DocumentStatus newStatus) {
        // TODO (STEP 8B): enforce state machine transitions
        this.status = newStatus;
    }

    public void markIndexed(Instant indexedAt) {
        // TODO (STEP 8B): enforce INDEXED is terminal
        this.status = DocumentStatus.INDEXED;
        this.indexedAt = indexedAt;
    }

    public DocumentId getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public JenisDokumen getDocumentType() { return documentType; }
    public JenisAkta getJenisAkta() { return jenisAkta; }
    public NomorAkta getNomorAkta() { return nomorAkta; }
    public LocalDate getTanggalAkta() { return tanggalAkta; }
    public ClassificationLevel getClassificationLevel() { return classificationLevel; }
    public DocumentStatus getStatus() { return status; }
    public String getMinioObjectKey() { return minioObjectKey; }
    public String getChecksumSha256() { return checksumSha256; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getMimeType() { return mimeType; }
    public UUID getNotarisId() { return notarisId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public int getVersionNumber() { return versionNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getIndexedAt() { return indexedAt; }
}
