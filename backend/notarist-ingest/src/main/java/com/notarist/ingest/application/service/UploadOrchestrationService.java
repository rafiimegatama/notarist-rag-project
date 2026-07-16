package com.notarist.ingest.application.service;

import com.notarist.core.domain.valueobject.*;
import com.notarist.ingest.api.response.UploadUrlResponse;
import com.notarist.ingest.application.command.InitiateIngestionCommand;
import com.notarist.ingest.application.port.in.InitiateIngestionUseCase;
import com.notarist.ingest.application.port.out.DocumentStoragePort;
import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.application.port.out.IngestQueueRepository;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import com.notarist.ingest.domain.service.ChecksumValidator;
import com.notarist.ingest.infrastructure.event.IngestionEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class UploadOrchestrationService implements InitiateIngestionUseCase {

    private final IngestJobRepository jobRepository;
    private final IngestQueueRepository queueRepository;
    private final DocumentStoragePort storagePort;
    private final DuplicateDetector duplicateDetector;
    private final IngestionEventPublisher eventPublisher;
    private final long signedUrlTtlSeconds;

    public UploadOrchestrationService(
            IngestJobRepository jobRepository,
            IngestQueueRepository queueRepository,
            DocumentStoragePort storagePort,
            DuplicateDetector duplicateDetector,
            IngestionEventPublisher eventPublisher,
            @Value("${notarist.ingest.signed-url-ttl-seconds:3600}") long signedUrlTtlSeconds) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.storagePort = storagePort;
        this.duplicateDetector = duplicateDetector;
        this.eventPublisher = eventPublisher;
        this.signedUrlTtlSeconds = signedUrlTtlSeconds;
    }

    @Override
    @Transactional
    public UploadUrlResponse execute(InitiateIngestionCommand command) {
        if (duplicateDetector.isDuplicate(command.checksum().sha256Hex(), command.tenantId())) {
            throw new IngestionStageException(
                    "INGEST_DUPLICATE_DOCUMENT", PipelineStatus.UPLOADED, false,
                    "Document with this checksum already exists for tenant: " + command.tenantId());
        }

        JobId jobId = new JobId(UUID.randomUUID());
        IngestionId ingestionId = IngestionId.of(jobId.value());
        DocumentId documentId = new DocumentId(UUID.randomUUID());

        IngestionJob job = new IngestionJob(
                ingestionId,
                jobId,
                documentId,
                command.tenantId(),
                command.uploadedBy(),
                command.checksum(),
                command.documentType(),
                command.classificationLevel(),
                command.originalFilename()
        );

        DocumentStoragePort.SignedUploadUrl signedUrl = storagePort.generateUploadUrl(
                command.tenantId(), documentId, jobId,
                command.originalFilename(),
                Duration.ofSeconds(signedUrlTtlSeconds));

        jobRepository.save(job);
        eventPublisher.publishUploadInitiated(job, command.correlationId().value());

        return new UploadUrlResponse(
                jobId.value(),
                signedUrl.signedUrl(),
                signedUrl.objectKey(),
                signedUrl.expiresAt(),
                signedUrl.requiredHeaders()
        );
    }

    @Transactional
    public void confirmUpload(UUID jobId, String confirmedChecksum, UUID callerTenantId) {
        IngestionJob job = jobRepository.findByJobId(new JobId(jobId))
                .orElseThrow(() -> new IngestionStageException(
                        "INGEST_JOB_NOT_FOUND", PipelineStatus.UPLOADED, false,
                        "Ingestion job not found: " + jobId));

        if (!job.getTenantId().equals(callerTenantId)) {
            throw new IngestionStageException(
                    "INGEST_UNAUTHORIZED", PipelineStatus.UPLOADED, false,
                    "Caller tenant does not match job tenant");
        }

        if (job.getPipelineStatus() != PipelineStatus.UPLOADED) {
            throw new IngestionStageException(
                    "INGEST_INVALID_STATE", job.getPipelineStatus(), false,
                    "Job is not in UPLOADED state, cannot confirm: " + job.getPipelineStatus());
        }

        ChecksumValidator.assertMatch(job.getChecksum(), confirmedChecksum, PipelineStatus.UPLOADED);

        if (!storagePort.verifyObjectExists(buildObjectKey(job))) {
            throw new IngestionStageException(
                    "INGEST_OBJECT_NOT_FOUND", PipelineStatus.UPLOADED, true,
                    "Uploaded object not found in storage for jobId: " + jobId);
        }

        job.transitionTo(PipelineStatus.OCR_PENDING);
        jobRepository.save(job);

        queueRepository.enqueue(
                job.getIngestionId(), job.getJobId(), job.getTenantId(),
                PipelineStatus.OCR_PENDING, "{}", Instant.now());

        eventPublisher.publishUploadConfirmed(job);
    }

    private String buildObjectKey(IngestionJob job) {
        return "notarist-raw/" + job.getTenantId() + "/" + job.getDocumentId().value();
    }
}
