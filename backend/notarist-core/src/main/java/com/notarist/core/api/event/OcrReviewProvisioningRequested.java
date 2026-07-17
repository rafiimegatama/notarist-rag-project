package com.notarist.core.api.event;

import java.util.UUID;

/**
 * Integration event: a document's OCR stage completed successfully, so its OCR-review landing rows
 * should be provisioned. Published by the ingestion pipeline (notarist-ingest) once the OCR stage has
 * committed; consumed by notarist-review to seed an {@code ocr_review} for the document.
 *
 * <p>It lives in {@code notarist-core} — the shared kernel every module depends on — for the same
 * reason {@link com.notarist.core.api.audit.AuditEventPayload} does: the producing and consuming
 * modules must agree on the payload without depending on each other's domain packages. notarist-review
 * cannot see an ingest-internal event type, and must not have to.
 *
 * <p>Deliberately carries only what OCR can actually assert. The per-field bounding boxes and the
 * stamp/signature findings that a full review needs are not in the OCR port's result contract
 * ({@code OcrServicePort.OcrResult}); they are the human reviewer's job — which is the entire purpose
 * of the OCR-review step. Provisioning creates the review shell (page count, overall confidence,
 * document identity); the reviewer fills the fields.
 *
 * @param documentId        the document whose OCR completed
 * @param tenantId          the owning tenant — the RLS identity the consumer must apply before writing
 * @param uploadedBy        the user who uploaded the document; recorded as the acting identity
 * @param documentName      display name for the review (the original upload filename)
 * @param pageCount         page count reported by OCR
 * @param overallConfidence average OCR confidence for the document
 */
public record OcrReviewProvisioningRequested(
        UUID documentId,
        UUID tenantId,
        UUID uploadedBy,
        String documentName,
        int pageCount,
        double overallConfidence
) {
    public OcrReviewProvisioningRequested {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (uploadedBy == null) throw new IllegalArgumentException("uploadedBy is required");
    }
}
