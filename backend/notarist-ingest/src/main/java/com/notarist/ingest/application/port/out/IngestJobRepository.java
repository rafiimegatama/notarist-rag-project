package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.domain.model.IngestionId;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestJobRepository {
    void save(IngestionJob job);
    Optional<IngestionJob> findByIngestionId(IngestionId ingestionId);
    Optional<IngestionJob> findByJobId(JobId jobId);
    /** All jobs for this checksum+tenant. Not unique — re-ingestion after FAILED/DLQ adds rows. */
    List<IngestionJob> findAllByChecksumAndTenantId(String checksumSha256, UUID tenantId);
    List<IngestionJob> findByStatusAndTenantId(PipelineStatus status, UUID tenantId, int limit);
    List<IngestionJob> findFailedAndReadyForRetry(int maxRetries, int limit);
}
