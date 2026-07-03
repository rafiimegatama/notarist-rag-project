package com.notarist.ingest.infrastructure.event;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Publishes domain events and audit payloads for all ingestion pipeline transitions. */
@Component
public class IngestionEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public IngestionEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishUploadInitiated(IngestionJob job, String correlationId) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_UPLOAD_INITIATED", "DOCUMENT", job.getDocumentId().value().toString(),
                job.getUploadedBy(), "UNKNOWN", job.getTenantId(),
                "UPLOAD_INITIATE", "SUCCESS", null, correlationId,
                Map.of(
                        "ingestionId", job.getIngestionId().toString(),
                        "documentType", job.getDocumentType().name(),
                        "checksum", job.getChecksum().sha256Hex()
                )
        ));
    }

    public void publishUploadConfirmed(IngestionJob job) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_UPLOAD_CONFIRMED", "DOCUMENT", job.getDocumentId().value().toString(),
                job.getUploadedBy(), "UNKNOWN", job.getTenantId(),
                "UPLOAD_CONFIRM", "SUCCESS", null, job.getIngestionId().toString(),
                Map.of("ingestionId", job.getIngestionId().toString())
        ));
    }

    public void publishStageCompleted(IngestionJob job, PipelineStatus completedStatus) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_STAGE_COMPLETED", "INGESTION_JOB",
                job.getIngestionId().toString(),
                job.getUploadedBy(), "SYSTEM", job.getTenantId(),
                completedStatus.name(), "SUCCESS", null, job.getIngestionId().toString(),
                Map.of(
                        "ingestionId", job.getIngestionId().toString(),
                        "stage", completedStatus.name(),
                        "documentId", job.getDocumentId().value().toString()
                )
        ));
    }

    public void publishStageFailure(IngestionJob job, PipelineStatus failedStatus, String errorCode) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_STAGE_FAILED", "INGESTION_JOB",
                job.getIngestionId().toString(),
                job.getUploadedBy(), "SYSTEM", job.getTenantId(),
                failedStatus.name(), "FAILURE", null, job.getIngestionId().toString(),
                Map.of(
                        "ingestionId", job.getIngestionId().toString(),
                        "stage", failedStatus.name(),
                        "errorCode", errorCode,
                        "retryCount", job.getRetryCount()
                )
        ));
    }

    public void publishDlqMoved(IngestionJob job, String errorCode) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_MOVED_TO_DLQ", "INGESTION_JOB",
                job.getIngestionId().toString(),
                job.getUploadedBy(), "SYSTEM", job.getTenantId(),
                "DLQ", "FAILURE", null, job.getIngestionId().toString(),
                Map.of(
                        "ingestionId", job.getIngestionId().toString(),
                        "errorCode", errorCode,
                        "retryCount", job.getRetryCount(),
                        "deadLetterReason", job.getDeadLetterReason() != null
                                ? job.getDeadLetterReason() : errorCode
                )
        ));
    }

    public void publishIngestionCompleted(IngestionJob job) {
        eventPublisher.publishEvent(new AuditEventPayload(
                "INGEST_PIPELINE_COMPLETED", "INGESTION_JOB",
                job.getIngestionId().toString(),
                job.getUploadedBy(), "SYSTEM", job.getTenantId(),
                "COMPLETE", "SUCCESS", null, job.getIngestionId().toString(),
                Map.of(
                        "ingestionId", job.getIngestionId().toString(),
                        "documentId", job.getDocumentId().value().toString(),
                        "completedAt", job.getCompletedAt() != null
                                ? job.getCompletedAt().toString() : ""
                )
        ));
    }
}
