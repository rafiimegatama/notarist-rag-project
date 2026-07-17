package com.notarist.review.api.response;

import com.notarist.review.domain.model.OcrReview;

import java.util.List;
import java.util.UUID;

/**
 * Read model for {@code GET /documents/{id}/ocr}. The top-level fields
 * ({@code documentId, documentName, pageCount, stampDetected, signatureDetected, overallConfidence,
 * fields, authorityTimeline}) are exactly what the existing OcrReviewScreen reads — the screen needs
 * no change. The rest ({@code reviewId, reviewStatus, reviewerId, reviewedAt, progress,
 * authorityItems}) is additive review detail.
 */
public record OcrReviewResponse(
        UUID documentId,
        String documentName,
        int pageCount,
        boolean stampDetected,
        boolean signatureDetected,
        double overallConfidence,
        List<OcrFieldResponse> fields,
        List<AuthorityTimelineEntryResponse> authorityTimeline,
        // ---- additive review detail ----
        UUID reviewId,
        String reviewStatus,
        UUID reviewerId,
        String reviewedAt,
        OcrReviewProgressResponse progress
) {
    public static OcrReviewResponse from(OcrReview r) {
        List<OcrFieldResponse> fields = r.fields().stream()
                .sorted(java.util.Comparator.comparingInt(f -> f.sortOrder()))
                .map(OcrFieldResponse::from)
                .toList();

        List<AuthorityTimelineEntryResponse> authority = r.authorityItems().stream()
                .sorted(java.util.Comparator.comparingInt(a -> a.sortOrder()))
                .map(AuthorityTimelineEntryResponse::from)
                .toList();

        return new OcrReviewResponse(
                r.documentId(),
                r.documentName(),
                r.pageCount(),
                r.stampDetected(),
                r.signatureDetected(),
                r.overallConfidence(),
                fields,
                authority,
                r.reviewId().value(),
                r.status().name(),
                r.reviewerId(),
                r.reviewedAt() != null ? r.reviewedAt().toString() : null,
                OcrReviewProgressResponse.from(r));
    }
}
