package com.notarist.ingest.infrastructure.persistence.postgres;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "INGESTION_JOB", indexes = {
        @Index(name = "IDX_INGEST_JOB_JOB_ID",        columnList = "JOB_ID"),
        @Index(name = "IDX_INGEST_JOB_TENANT_STATUS",  columnList = "TENANT_ID,PIPELINE_STATUS"),
        @Index(name = "IDX_INGEST_JOB_CHECKSUM_TENANT", columnList = "CHECKSUM_SHA256,TENANT_ID"),
        @Index(name = "IDX_INGEST_JOB_RETRY",          columnList = "PIPELINE_STATUS,NEXT_RETRY_AT")
})
public class IngestionJobJpaEntity {

    @Id
    @Column(name = "INGESTION_ID", length = 36, nullable = false)
    private String ingestionId;

    @Column(name = "JOB_ID", length = 36, nullable = false, unique = true)
    private String jobId;

    @Column(name = "DOCUMENT_ID", length = 36, nullable = false)
    private String documentId;

    @Column(name = "TENANT_ID", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "UPLOADED_BY", length = 36, nullable = false)
    private String uploadedBy;

    @Column(name = "DOCUMENT_TYPE", length = 50, nullable = false)
    private String documentType;

    @Column(name = "CLASSIFICATION_LEVEL", length = 50, nullable = false)
    private String classificationLevel;

    @Column(name = "ORIGINAL_FILENAME", length = 500, nullable = false)
    private String originalFilename;

    @Column(name = "CHECKSUM_SHA256", length = 64, nullable = false)
    private String checksumSha256;

    @Column(name = "PIPELINE_STATUS", length = 50, nullable = false)
    private String pipelineStatus;

    @Column(name = "OVERALL_STATUS", length = 50, nullable = false)
    private String overallStatus;

    @Column(name = "FAILURE_STAGE", length = 50)
    private String failureStage;

    @Column(name = "RETRY_COUNT", nullable = false)
    private int retryCount;

    @Column(name = "LAST_ERROR_CODE", length = 100)
    private String lastErrorCode;

    @Column(name = "LAST_ERROR_HASH", length = 64)
    private String lastErrorHash;

    @Column(name = "NEXT_RETRY_AT")
    private Instant nextRetryAt;

    @Column(name = "DEAD_LETTER_REASON", length = 1000)
    private String deadLetterReason;

    @Column(name = "OCR_CONFIDENCE")
    private Float ocrConfidence;

    @Column(name = "OCR_OBJECT_KEY", length = 1000)
    private String ocrObjectKey;

    // Deliberately NOT @Lob. On Oracle that mapped to CLOB, but on PostgreSQL Hibernate maps a
    // @Lob String to a large-object OID — the column would hold an integer handle, not the JSON,
    // and reads fail with "Bad value for type long". The column is plain TEXT (Flyway V8) and a
    // String binds to it directly.
    @Column(name = "STAGE_HISTORY", nullable = false, columnDefinition = "text")
    private String stageHistoryJson;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    protected IngestionJobJpaEntity() {}

    public IngestionJobJpaEntity(
            String ingestionId, String jobId, String documentId, String tenantId,
            String uploadedBy, String documentType, String classificationLevel,
            String originalFilename, String checksumSha256, String pipelineStatus,
            String overallStatus, String failureStage, int retryCount,
            String lastErrorCode, String lastErrorHash, Instant nextRetryAt,
            String deadLetterReason, Float ocrConfidence, String ocrObjectKey,
            String stageHistoryJson,
            Instant createdAt, Instant updatedAt, Instant completedAt) {
        this.ingestionId = ingestionId;
        this.jobId = jobId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.documentType = documentType;
        this.classificationLevel = classificationLevel;
        this.originalFilename = originalFilename;
        this.checksumSha256 = checksumSha256;
        this.pipelineStatus = pipelineStatus;
        this.overallStatus = overallStatus;
        this.failureStage = failureStage;
        this.retryCount = retryCount;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorHash = lastErrorHash;
        this.nextRetryAt = nextRetryAt;
        this.deadLetterReason = deadLetterReason;
        this.ocrConfidence = ocrConfidence;
        this.ocrObjectKey = ocrObjectKey;
        this.stageHistoryJson = stageHistoryJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    public String getIngestionId() { return ingestionId; }
    public String getJobId() { return jobId; }
    public String getDocumentId() { return documentId; }
    public String getTenantId() { return tenantId; }
    public String getUploadedBy() { return uploadedBy; }
    public String getDocumentType() { return documentType; }
    public String getClassificationLevel() { return classificationLevel; }
    public String getOriginalFilename() { return originalFilename; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getPipelineStatus() { return pipelineStatus; }
    public String getOverallStatus() { return overallStatus; }
    public String getFailureStage() { return failureStage; }
    public int getRetryCount() { return retryCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorHash() { return lastErrorHash; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getDeadLetterReason() { return deadLetterReason; }
    public Float getOcrConfidence() { return ocrConfidence; }
    public String getOcrObjectKey() { return ocrObjectKey; }
    public String getStageHistoryJson() { return stageHistoryJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setPipelineStatus(String pipelineStatus) { this.pipelineStatus = pipelineStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public void setLastErrorHash(String lastErrorHash) { this.lastErrorHash = lastErrorHash; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public void setDeadLetterReason(String deadLetterReason) { this.deadLetterReason = deadLetterReason; }
    public void setOcrConfidence(Float ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public void setOcrObjectKey(String ocrObjectKey) { this.ocrObjectKey = ocrObjectKey; }
    public void setStageHistoryJson(String stageHistoryJson) { this.stageHistoryJson = stageHistoryJson; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
