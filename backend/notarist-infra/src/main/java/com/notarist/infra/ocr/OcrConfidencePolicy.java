package com.notarist.infra.ocr;

/**
 * OCR confidence policy for the legal document domain.
 *
 * Legal documents require higher accuracy than general-purpose OCR.
 * A chunk with low OCR confidence MUST NOT be made searchable immediately:
 *   - it goes into LOW_CONFIDENCE_REVIEW queue (is_searchable = false in Qdrant)
 *   - a notaris or authorized reviewer must approve it before it becomes searchable
 *   - in QdrantIndexAdapter, is_searchable is set based on OcrReviewStatus
 *
 * Thresholds:
 *   ACCEPTED (>=0.80): high enough for direct indexing in legal domain
 *   LOW_CONFIDENCE_REVIEW (>=0.40 and <0.80): must be queued for review
 *   REJECTED (<0.40): OCR output is too poor; document must be re-scanned
 */
public final class OcrConfidencePolicy {

    private OcrConfidencePolicy() {}

    public static final float ACCEPTED_THRESHOLD = 0.80f;
    public static final float REVIEW_THRESHOLD   = 0.40f;

    public static OcrReviewStatus evaluate(float confidenceScore) {
        if (confidenceScore >= ACCEPTED_THRESHOLD)  return OcrReviewStatus.ACCEPTED;
        if (confidenceScore >= REVIEW_THRESHOLD)    return OcrReviewStatus.LOW_CONFIDENCE_REVIEW;
        return OcrReviewStatus.REJECTED;
    }

    public static boolean isSearchable(float confidenceScore) {
        return evaluate(confidenceScore) == OcrReviewStatus.ACCEPTED;
    }
}
