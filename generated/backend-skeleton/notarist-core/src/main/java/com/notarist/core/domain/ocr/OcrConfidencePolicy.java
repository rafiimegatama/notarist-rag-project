package com.notarist.core.domain.ocr;

/**
 * OCR confidence thresholds for the legal document domain.
 *
 * Legal documents require higher accuracy than general-purpose OCR:
 *   ACCEPTED (>=0.80)                 — high enough for direct indexing
 *   LOW_CONFIDENCE_REVIEW (>=0.40, <0.80) — queue for human review; is_searchable=false
 *   REJECTED (<0.40)                  — OCR output too poor; document must be re-scanned
 *
 * Canonical location: notarist-core.
 * No infrastructure dependencies — pure domain policy.
 */
public final class OcrConfidencePolicy {

    public static final float ACCEPTED_THRESHOLD = 0.80f;
    public static final float REVIEW_THRESHOLD   = 0.40f;

    private OcrConfidencePolicy() {}

    public static OcrReviewStatus evaluate(float confidenceScore) {
        if (confidenceScore >= ACCEPTED_THRESHOLD)  return OcrReviewStatus.ACCEPTED;
        if (confidenceScore >= REVIEW_THRESHOLD)    return OcrReviewStatus.LOW_CONFIDENCE_REVIEW;
        return OcrReviewStatus.REJECTED;
    }

    public static boolean isSearchable(float confidenceScore) {
        return evaluate(confidenceScore) == OcrReviewStatus.ACCEPTED;
    }
}
