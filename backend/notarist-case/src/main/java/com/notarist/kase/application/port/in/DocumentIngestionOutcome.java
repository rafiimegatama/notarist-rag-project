package com.notarist.kase.application.port.in;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

/**
 * What the Case context needs to know about a document that finished the ingestion pipeline.
 *
 * <p><b>This is an inbound port, deliberately shaped in the Case context's own language.</b> It is NOT
 * the ingest module's event type, and the Case module does not import one. {@code notarist-case} and
 * {@code notarist-ingest} have no dependency on each other in either direction — ArchUnit enforces
 * both — because the machine pipeline must never know what a Case is, and the human workflow must
 * never block on worker mechanics.
 *
 * <p>The composition root translates the ingest event into this port. The pipeline simply echoes back
 * the caseId/bundleId it was handed in the existing job payload; it never resolves one.
 */
public record DocumentIngestionOutcome(
        DocumentId documentId,
        CaseId caseId,
        BundleId bundleId,
        boolean succeeded
) {
    public DocumentIngestionOutcome {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (caseId == null)     throw new IllegalArgumentException("caseId is required");
    }
}
