package com.notarist.review.api.response;

import com.notarist.review.domain.model.OcrReview;

import java.util.UUID;

/** Read model for {@code GET /ocr/summary} — review progress at a glance. */
public record OcrReviewSummaryResponse(
        UUID documentId,
        UUID reviewId,
        String reviewStatus,
        OcrReviewProgressResponse progress
) {
    public static OcrReviewSummaryResponse from(OcrReview r) {
        return new OcrReviewSummaryResponse(
                r.documentId(),
                r.reviewId().value(),
                r.status().name(),
                OcrReviewProgressResponse.from(r));
    }
}
