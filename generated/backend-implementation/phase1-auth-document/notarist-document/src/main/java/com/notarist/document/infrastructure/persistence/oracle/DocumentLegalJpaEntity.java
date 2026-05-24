package com.notarist.document.infrastructure.persistence.oracle;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "DOKUMEN_LEGAL", schema = "NOTARIST")
public class DocumentLegalJpaEntity {

    @Id
    @Column(name = "DOCUMENT_ID", length = 36, nullable = false)
    private String documentId;

    @Column(name = "DOCUMENT_TITLE", length = 500, nullable = false)
    private String documentTitle;

    @Column(name = "DOCUMENT_TYPE", length = 50, nullable = false)
    private String documentType;

    @Column(name = "JENIS_AKTA", length = 50)
    private String jenisAkta;

    @Column(name = "NOMOR_AKTA", length = 100)
    private String nomorAkta;

    @Column(name = "TANGGAL_AKTA")
    private LocalDate tanggalAkta;

    @Column(name = "CLASSIFICATION_LEVEL", length = 50, nullable = false)
    private String classificationLevel;

    @Column(name = "STATUS", length = 50, nullable = false)
    private String status;

    @Column(name = "MINIO_OBJECT_KEY", length = 500, nullable = false)
    private String minioObjectKey;

    @Column(name = "CHECKSUM_SHA256", length = 64, nullable = false)
    private String checksumSha256;

    @Column(name = "FILE_SIZE_BYTES")
    private Long fileSizeBytes;

    @Column(name = "MIME_TYPE", length = 100)
    private String mimeType;

    @Column(name = "NOTARIS_ID", length = 36)
    private String notarisId;

    @Column(name = "TENANT_ID", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "UPLOADED_BY", length = 36, nullable = false)
    private String uploadedBy;

    @Column(name = "PAGE_COUNT")
    private Integer pageCount;

    @Column(name = "VERSION_NUMBER", nullable = false)
    private int versionNumber;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "INDEXED_AT")
    private Instant indexedAt;

    protected DocumentLegalJpaEntity() {}

    public DocumentLegalJpaEntity(
            String documentId, String documentTitle, String documentType, String jenisAkta,
            String nomorAkta, LocalDate tanggalAkta, String classificationLevel, String status,
            String minioObjectKey, String checksumSha256, Long fileSizeBytes, String mimeType,
            String notarisId, String tenantId, String uploadedBy, Integer pageCount,
            int versionNumber, Instant createdAt, Instant indexedAt) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentType = documentType;
        this.jenisAkta = jenisAkta;
        this.nomorAkta = nomorAkta;
        this.tanggalAkta = tanggalAkta;
        this.classificationLevel = classificationLevel;
        this.status = status;
        this.minioObjectKey = minioObjectKey;
        this.checksumSha256 = checksumSha256;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.notarisId = notarisId;
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.pageCount = pageCount;
        this.versionNumber = versionNumber;
        this.createdAt = createdAt;
        this.indexedAt = indexedAt;
    }

    public String getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public String getDocumentType() { return documentType; }
    public String getJenisAkta() { return jenisAkta; }
    public String getNomorAkta() { return nomorAkta; }
    public LocalDate getTanggalAkta() { return tanggalAkta; }
    public String getClassificationLevel() { return classificationLevel; }
    public String getStatus() { return status; }
    public String getMinioObjectKey() { return minioObjectKey; }
    public String getChecksumSha256() { return checksumSha256; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getNotarisId() { return notarisId; }
    public String getTenantId() { return tenantId; }
    public String getUploadedBy() { return uploadedBy; }
    public Integer getPageCount() { return pageCount; }
    public int getVersionNumber() { return versionNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getIndexedAt() { return indexedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
}
