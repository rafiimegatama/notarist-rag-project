package com.notarist.ingest.application.port.in;

import com.notarist.core.domain.valueobject.JobId;
import com.notarist.ingest.api.response.IngestionJobStatusResponse;

public interface GetIngestionStatusUseCase {
    IngestionJobStatusResponse execute(JobId jobId, java.util.UUID callerTenantId);
}
