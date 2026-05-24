package com.notarist.core.domain.ocr;

/**
 * OCR review status for legal documents.
 *
 * ACCEPTED             — confidence >= 0.80; directly searchable
 * LOW_CONFIDENCE_REVIEW — confidence [0.40, 0.80); queued for human review; is_searchable=false
 * REJECTED             — confidence < 0.40; document must be re-scanned; not indexed
 *
 * Canonical location: notarist-core.
 * Shared by notarist-ingest (ingestion pipeline) and notarist-runtime (PaddleOCR adapter).
 */
public enum OcrReviewStatus {

    ACCEPTED,
    LOW_CONFIDENCE_REVIEW,
    REJECTED;

    public boolean isSearchable() {
        return this == ACCEPTED;
    }

    public boolean requiresHumanReview() {
        return this == LOW_CONFIDENCE_REVIEW;
    }
}
