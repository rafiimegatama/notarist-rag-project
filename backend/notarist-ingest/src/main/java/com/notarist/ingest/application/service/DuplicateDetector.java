package com.notarist.ingest.application.service;

import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.JobStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Checks for duplicate documents via checksum + tenant, scoped to the ingest job table. */
@Service
public class DuplicateDetector {

    private final IngestJobRepository jobRepository;

    public DuplicateDetector(IngestJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Returns true if a non-failed ingestion job with the same checksum+tenant exists.
     * Allows re-ingestion if the previous job failed or moved to DLQ.
     */
    public boolean isDuplicate(String checksumSha256, UUID tenantId) {
        Optional<IngestionJob> existing =
                jobRepository.findByChecksumAndTenantId(checksumSha256, tenantId);
        return existing
                .filter(job -> job.getOverallStatus() != JobStatus.FAILED
                        && job.getOverallStatus() != JobStatus.DLQ)
                .isPresent();
    }
}
