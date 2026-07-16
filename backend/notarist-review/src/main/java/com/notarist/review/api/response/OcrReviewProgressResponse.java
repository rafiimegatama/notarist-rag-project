package com.notarist.review.api.response;

import com.notarist.review.domain.model.OcrReview;

/** Review progress counters — accepted / corrected / rejected / remaining out of total. */
public record OcrReviewProgressResponse(
        int total,
        long accepted,
        long corrected,
        long rejected,
        long remaining
) {
    public static OcrReviewProgressResponse from(OcrReview r) {
        return new OcrReviewProgressResponse(
                r.totalFields(),
                r.acceptedCount(),
                r.correctedCount(),
                r.rejectedCount(),
                r.remainingCount());
    }
}
