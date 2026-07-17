package com.notarist.core.api.event;

import java.util.UUID;

/**
 * Integration event: a document reached a terminal state in the ingestion pipeline — either the whole
 * pipeline COMPLETED, or the document was dead-lettered. Published by notarist-ingest; consumed by
 * notarist-case to advance the owning case (which observes ingestion, never drives it).
 *
 * <p>Lives in {@code notarist-core} so the two modules stay fully decoupled: {@code notarist-ingest}
 * and {@code notarist-case} depend on neither each other nor this event's counterpart types (ArchUnit
 * enforces that they never reference one another). The pipeline announces only the plain fact — a
 * document id, its tenant, and whether it succeeded — and knows nothing about cases or bundles. The
 * case side resolves the owning case from the document id through its own persistence.
 *
 * @param documentId the document that finished ingesting
 * @param tenantId   the owning tenant — the RLS identity the consumer must apply before reading/writing
 * @param succeeded  true if the pipeline COMPLETED; false if the document was moved to the DLQ
 */
public record DocumentIngestionCompleted(
        UUID documentId,
        UUID tenantId,
        boolean succeeded
) {
    public DocumentIngestionCompleted {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
    }
}
