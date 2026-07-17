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
     *
     * <p>Asks across ALL jobs for the checksum, because there can legitimately be several. This
     * previously read a single Optional, which made the rule destroy itself: allowing re-ingestion
     * after a DLQ is exactly what creates a second row with that checksum, and the next upload of
     * the same file then blew up on IncorrectResultSizeDataAccessException ("2 results were
     * returned") — HTTP 500 on POST /ingest, for a document that was not even a duplicate. Any
     * live job wins over any number of dead ones.
     */
    public boolean isDuplicate(String checksumSha256, UUID tenantId) {
        return jobRepository.findAllByChecksumAndTenantId(checksumSha256, tenantId)
                .stream()
                .anyMatch(job -> job.getOverallStatus() != JobStatus.FAILED
                        && job.getOverallStatus() != JobStatus.DLQ);
    }
}
