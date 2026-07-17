package com.notarist.review.api.response;

import com.notarist.review.domain.model.FieldReview;
import com.notarist.review.domain.state.FieldDecision;

/**
 * Read model for one reviewed field.
 *
 * <p>The {@code id}, {@code label}, {@code value}, {@code confidence}, {@code status} and {@code bbox}
 * fields (bbox keyed {@code x,y,w,h}) are exactly what the existing OcrReviewScreen consumes — do not
 * rename them. The remaining fields are additive detail the richer review model carries.
 */
public record OcrFieldResponse(
        String id,
        String label,
        String value,
        double confidence,
        String status,              // frontend vocabulary: PENDING|NEEDS_CHECK|APPROVED|REJECTED
        Bbox bbox,
        // ---- additive review detail ----
        String fieldName,
        String extractedValue,
        String correctedValue,
        String confidenceLevel,     // HIGH|MEDIUM|LOW
        String decision,            // FieldDecision
        String rejectionReason,
        int page
) {
    /** Relative bounding box. Keys are {@code x,y,w,h} to match the existing frontend overlay. */
    public record Bbox(double x, double y, double w, double h) {}

    public static OcrFieldResponse from(FieldReview f) {
        return new OcrFieldResponse(
                f.fieldId().toString(),
                f.displayLabel(),
                f.effectiveValue(),
                f.confidence(),
                frontendStatus(f),
                new Bbox(f.boundingBox().x(), f.boundingBox().y(),
                        f.boundingBox().width(), f.boundingBox().height()),
                f.fieldName(),
                f.extractedValue(),
                f.correctedValue(),
                f.confidenceLevel().name(),
                f.decision().name(),
                f.rejectionReason(),
                f.boundingBox().page());
    }

    /** Maps the rich {@link FieldDecision} onto the four states the frontend row understands. */
    private static String frontendStatus(FieldReview f) {
        FieldDecision d = f.decision();
        if (d == FieldDecision.REJECTED) return "REJECTED";
        if (d.isAccepted()) return "APPROVED";
        // NEEDS_REVIEW: PENDING until a human has looked, then NEEDS_CHECK.
        return f.reviewedAt() == null ? "PENDING" : "NEEDS_CHECK";
    }
}
