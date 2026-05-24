package com.notarist.ingest.application.port.out;

import com.notarist.ingest.domain.model.DeadLetterEntry;
import com.notarist.ingest.domain.model.IngestionId;

import java.util.List;
import java.util.UUID;

/** Port for DLQ persistence — append-only. */
public interface DeadLetterRepository {
    void save(DeadLetterEntry entry);
    List<DeadLetterEntry> findByTenantId(UUID tenantId, int limit);
    List<DeadLetterEntry> findByIngestionId(IngestionId ingestionId);
}
