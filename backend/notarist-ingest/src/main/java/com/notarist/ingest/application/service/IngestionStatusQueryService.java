package com.notarist.ingest.application.service;

import com.notarist.ingest.api.response.IngestionStatusResponse;
import com.notarist.ingest.application.port.in.GetIngestionStatusUseCase;
import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IngestionStatusQueryService implements GetIngestionStatusUseCase {

    private final IngestJobRepository jobRepository;

    public IngestionStatusQueryService(IngestJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IngestionStatusResponse getStatus(IngestionId ingestionId, UUID callerTenantId) {
        IngestionJob job = jobRepository.findByIngestionId(ingestionId)
                .orElseThrow(() -> new IngestionStageException(
                        "INGEST_JOB_NOT_FOUND", PipelineStatus.UPLOADED, false,
                        "Ingestion job not found: " + ingestionId));

        if (!job.getTenantId().equals(callerTenantId)) {
            throw new IngestionStageException(
                    "INGEST_UNAUTHORIZED", job.getPipelineStatus(), false,
                    "Caller tenant does not match job tenant");
        }

        List<IngestionStatusResponse.StageEntry> history = job.getStageHistory().stream()
                .map(r -> new IngestionStatusResponse.StageEntry(
                        r.stage().name(),
                        r.completedAt().toString(),
                        r.durationMs(),
                        r.errorCode(),
                        r.attemptNumber()))
                .toList();

        return new IngestionStatusResponse(
                job.getIngestionId().value(),
                job.getJobId().value(),
                job.getDocumentId().value(),
                job.getPipelineStatus(),
                job.getOverallStatus().name(),
                job.getRetryCount(),
                job.getLastErrorCode(),
                job.getFailureStage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt(),
                history
        );
    }
}
