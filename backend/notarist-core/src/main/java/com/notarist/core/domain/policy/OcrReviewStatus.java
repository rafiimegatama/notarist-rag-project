package com.notarist.core.domain.policy;

/**
 * OCR review status for legal documents.
 *
 * ACCEPTED           — confidence >= 0.80; directly searchable
 * LOW_CONFIDENCE_REVIEW — confidence [0.40, 0.80); queued for human review; is_searchable=false
 * REJECTED           — confidence < 0.40; document must be re-scanned; not indexed
 *
 * Stored on IngestionJob and propagated to the chunk index / Qdrant payload via is_searchable flag.
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
