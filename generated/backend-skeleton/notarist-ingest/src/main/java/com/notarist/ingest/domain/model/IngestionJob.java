package com.notarist.ingest.domain.model;

import com.notarist.core.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the ingestion pipeline.
 * Tracks status of one document through all 5 pipeline stages.
 */
public class IngestionJob {

    private final JobId jobId;
    private final DocumentId documentId;
    private final UUID tenantId;
    private final UUID uploadedBy;
    private final DocumentChecksum checksum;
    private final JenisDokumen documentType;
    private final ClassificationLevel classificationLevel;
    private PipelineStage currentStage;
    private JobStatus status;
    private final List<StageRecord> stageHistory;
    private String failureReason;
    private int attemptCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public IngestionJob(
            JobId jobId,
            DocumentId documentId,
            UUID tenantId,
            UUID uploadedBy,
            DocumentChecksum checksum,
            JenisDokumen documentType,
            ClassificationLevel classificationLevel) {
        this.jobId = jobId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.checksum = checksum;
        this.documentType = documentType;
        this.classificationLevel = classificationLevel;
        this.currentStage = PipelineStage.UPLOAD_CONFIRMED;
        this.status = JobStatus.PENDING;
        this.stageHistory = new ArrayList<>();
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // TODO (STEP 8B): implement stage transition validation
    public void advanceToStage(PipelineStage nextStage) {
        this.currentStage = nextStage;
        this.status = JobStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markStageCompleted(PipelineStage stage, long durationMs) {
        // TODO (STEP 8B): record stage completion
        this.stageHistory.add(new StageRecord(stage, Instant.now(), durationMs, null));
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = JobStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void moveToDlq() {
        this.status = JobStatus.DLQ;
        this.updatedAt = Instant.now();
    }

    public JobId getJobId() { return jobId; }
    public DocumentId getDocumentId() { return documentId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public DocumentChecksum getChecksum() { return checksum; }
    public JenisDokumen getDocumentType() { return documentType; }
    public ClassificationLevel getClassificationLevel() { return classificationLevel; }
    public PipelineStage getCurrentStage() { return currentStage; }
    public JobStatus getStatus() { return status; }
    public List<StageRecord> getStageHistory() { return List.copyOf(stageHistory); }
    public String getFailureReason() { return failureReason; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public record StageRecord(
        PipelineStage stage,
        Instant completedAt,
        long durationMs,
        String error
    ) {}
}
