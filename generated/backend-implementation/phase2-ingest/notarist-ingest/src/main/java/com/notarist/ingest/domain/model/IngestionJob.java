package com.notarist.ingest.domain.model;

import com.notarist.core.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the ingestion pipeline — Phase 2 implementation.
 * Supersedes skeleton version. Uses PipelineStatus (not PipelineStage) for state.
 * All state transitions enforced via PipelineStateMachine domain service.
 */
public class IngestionJob {

    private final IngestionId ingestionId;
    private final JobId jobId;
    private final DocumentId documentId;
    private final UUID tenantId;
    private final UUID uploadedBy;
    private final DocumentChecksum checksum;
    private final JenisDokumen documentType;
    private final ClassificationLevel classificationLevel;
    private final String originalFilename;

    private PipelineStatus pipelineStatus;
    private JobStatus overallStatus;

    private final List<StageRecord> stageHistory;

    private String failureStage;
    private int retryCount;
    private String lastErrorCode;
    private String lastErrorHash;
    private Instant nextRetryAt;
    private String deadLetterReason;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    /** Reconstruction factory for persistence layer — bypasses normal pipeline initialization. */
    public static IngestionJob reconstruct(
            IngestionId ingestionId, JobId jobId, DocumentId documentId,
            UUID tenantId, UUID uploadedBy, DocumentChecksum checksum,
            JenisDokumen documentType, ClassificationLevel classificationLevel,
            String originalFilename, PipelineStatus pipelineStatus, JobStatus overallStatus,
            List<StageRecord> stageHistory, String failureStage, int retryCount,
            String lastErrorCode, String lastErrorHash, Instant nextRetryAt,
            String deadLetterReason, Instant createdAt, Instant updatedAt, Instant completedAt) {

        IngestionJob job = new IngestionJob(ingestionId, jobId, documentId, tenantId, uploadedBy,
                checksum, documentType, classificationLevel, originalFilename);
        job.pipelineStatus = pipelineStatus;
        job.overallStatus = overallStatus;
        job.stageHistory.clear();
        if (stageHistory != null) job.stageHistory.addAll(stageHistory);
        job.failureStage = failureStage;
        job.retryCount = retryCount;
        job.lastErrorCode = lastErrorCode;
        job.lastErrorHash = lastErrorHash;
        job.nextRetryAt = nextRetryAt;
        job.deadLetterReason = deadLetterReason;
        job.createdAt = createdAt;
        job.updatedAt = updatedAt;
        job.completedAt = completedAt;
        return job;
    }

    public IngestionJob(
            IngestionId ingestionId,
            JobId jobId,
            DocumentId documentId,
            UUID tenantId,
            UUID uploadedBy,
            DocumentChecksum checksum,
            JenisDokumen documentType,
            ClassificationLevel classificationLevel,
            String originalFilename) {
        this.ingestionId = ingestionId;
        this.jobId = jobId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.checksum = checksum;
        this.documentType = documentType;
        this.classificationLevel = classificationLevel;
        this.originalFilename = originalFilename;
        this.pipelineStatus = PipelineStatus.UPLOADED;
        this.overallStatus = JobStatus.PENDING;
        this.stageHistory = new ArrayList<>();
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void transitionTo(PipelineStatus nextStatus) {
        PipelineStateMachine.assertValidTransition(pipelineStatus, nextStatus);
        this.pipelineStatus = nextStatus;
        this.overallStatus = resolveOverallStatus(nextStatus);
        this.updatedAt = Instant.now();
    }

    public void recordStageCompletion(PipelineStatus completedStage, long durationMs) {
        stageHistory.add(new StageRecord(completedStage, Instant.now(), durationMs, null, retryCount));
        this.updatedAt = Instant.now();
    }

    public void recordStageFailure(PipelineStatus failedStage, String errorCode, long durationMs) {
        stageHistory.add(new StageRecord(failedStage, Instant.now(), durationMs, errorCode, retryCount));
        this.failureStage = failedStage.name();
        this.lastErrorCode = errorCode;
        this.lastErrorHash = hashError(errorCode);
        this.pipelineStatus = PipelineStatus.FAILED;
        this.overallStatus = JobStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void scheduleRetry(Instant retryAt) {
        this.retryCount++;
        this.nextRetryAt = retryAt;
        this.pipelineStatus = PipelineStatus.FAILED;
        this.overallStatus = JobStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void moveToDlq(String reason) {
        this.pipelineStatus = PipelineStatus.DLQ;
        this.overallStatus = JobStatus.DLQ;
        this.deadLetterReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.pipelineStatus = PipelineStatus.COMPLETED;
        this.overallStatus = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isEligibleForRetry(int maxRetries) {
        return retryCount < maxRetries && pipelineStatus == PipelineStatus.FAILED;
    }

    private static JobStatus resolveOverallStatus(PipelineStatus status) {
        return switch (status) {
            case COMPLETED -> JobStatus.COMPLETED;
            case DLQ, FAILED -> JobStatus.FAILED;
            case UPLOADED -> JobStatus.PENDING;
            default -> JobStatus.PROCESSING;
        };
    }

    private static String hashError(String errorCode) {
        if (errorCode == null) return null;
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(errorCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    public IngestionId getIngestionId() { return ingestionId; }
    public JobId getJobId() { return jobId; }
    public DocumentId getDocumentId() { return documentId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public DocumentChecksum getChecksum() { return checksum; }
    public JenisDokumen getDocumentType() { return documentType; }
    public ClassificationLevel getClassificationLevel() { return classificationLevel; }
    public String getOriginalFilename() { return originalFilename; }
    public PipelineStatus getPipelineStatus() { return pipelineStatus; }
    public JobStatus getOverallStatus() { return overallStatus; }
    public List<StageRecord> getStageHistory() { return List.copyOf(stageHistory); }
    public String getFailureStage() { return failureStage; }
    public int getRetryCount() { return retryCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorHash() { return lastErrorHash; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getDeadLetterReason() { return deadLetterReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public record StageRecord(
            PipelineStatus stage,
            Instant completedAt,
            long durationMs,
            String errorCode,
            int attemptNumber
    ) {}
}
