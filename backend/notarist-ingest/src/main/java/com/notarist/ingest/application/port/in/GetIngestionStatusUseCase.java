package com.notarist.ingest.application.port.in;

import com.notarist.ingest.api.response.IngestionStatusResponse;
import com.notarist.ingest.domain.model.IngestionId;

import java.util.UUID;

public interface GetIngestionStatusUseCase {
    IngestionStatusResponse getStatus(IngestionId ingestionId, UUID callerTenantId);
}
