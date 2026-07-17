package com.notarist.review.domain.model;

import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import com.notarist.review.domain.state.ConfidenceLevel;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.valueobject.BoundingBox;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.Reviewer;

import java.time.Instant;

/**
 * One extracted field and the reviewer's decision on it. A child entity of {@link OcrReview}: its
 * mutators are package-visible, so the aggregate is the only thing that can move a field. A service
 * cannot reach in and set a decision past the aggregate's rules.
 *
 * <p><b>The extracted value is immutable.</b> A correction is written to {@code correctedValue}; the
 * OCR output is never overwritten, so the original is always recoverable — from the field and from
 * the append-only audit trail.
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>{@code AUTO_ACCEPTED} is legal only for HIGH confidence — LOW/MEDIUM require a human decision.</li>
 *   <li>{@code CORRECTED} requires a non-blank new value and preserves the original.</li>
 *   <li>{@code REJECTED} requires a non-blank reason.</li>
 * </ul>
 */
public final class FieldReview {

    private final FieldId fieldId;
    private final String fieldName;
    private final String displayLabel;
    private final String extractedValue;         // OCR output — never overwritten
    private final double confidence;
    private final ConfidenceLevel confidenceLevel;
    private final BoundingBox boundingBox;
    private final int sortOrder;

    private String correctedValue;
    private FieldDecision decision;
    private String rejectionReason;
    private java.util.UUID reviewerId;
    private Instant reviewedAt;

    private FieldReview(FieldId fieldId, String fieldName, String displayLabel, String extractedValue,
                        double confidence, BoundingBox boundingBox, int sortOrder,
                        String correctedValue, FieldDecision decision, String rejectionReason,
                        java.util.UUID reviewerId, Instant reviewedAt) {
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.displayLabel = displayLabel;
        this.extractedValue = extractedValue;
        this.confidence = confidence;
        this.confidenceLevel = ConfidenceLevel.from(confidence);
        this.boundingBox = boundingBox;
        this.sortOrder = sortOrder;
        this.correctedValue = correctedValue;
        this.decision = decision;
        this.rejectionReason = rejectionReason;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
    }

    /** A newly extracted, not-yet-reviewed field. Starts NEEDS_REVIEW. */
    public static FieldReview extracted(FieldId fieldId, String fieldName, String displayLabel,
                                        String extractedValue, double confidence,
                                        BoundingBox boundingBox, int sortOrder) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new ReviewInvariantViolationException("field name is required");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new ReviewInvariantViolationException("confidence must be within [0,1] (was " + confidence + ")");
        }
        return new FieldReview(fieldId, fieldName,
                displayLabel == null || displayLabel.isBlank() ? fieldName : displayLabel,
                extractedValue, confidence, boundingBox, sortOrder,
                null, FieldDecision.NEEDS_REVIEW, null, null, null);
    }

    /** Rehydration from persistence — no rules re-applied, the row is already valid. */
    public static FieldReview rehydrate(FieldId fieldId, String fieldName, String displayLabel,
                                        String extractedValue, String correctedValue, double confidence,
                                        FieldDecision decision, String rejectionReason,
                                        java.util.UUID reviewerId, Instant reviewedAt,
                                        BoundingBox boundingBox, int sortOrder) {
        return new FieldReview(fieldId, fieldName, displayLabel, extractedValue, confidence,
                boundingBox, sortOrder, correctedValue, decision, rejectionReason, reviewerId, reviewedAt);
    }

    // ---- Mutators — package-visible, driven only by OcrReview -----------------------------------

    void autoAccept(Reviewer reviewer) {
        if (confidenceLevel != ConfidenceLevel.HIGH) {
            throw new ReviewInvariantViolationException(
                    "Field '" + fieldName + "' has " + confidenceLevel
                            + " confidence — only HIGH confidence may be auto-accepted; a human decision is required");
        }
        settle(FieldDecision.AUTO_ACCEPTED, null, reviewer);
    }

    void manualAccept(Reviewer reviewer) {
        settle(FieldDecision.MANUAL_ACCEPTED, null, reviewer);
    }

    void correct(String newValue, Reviewer reviewer) {
        if (newValue == null || newValue.isBlank()) {
            throw new ReviewInvariantViolationException(
                    "A correction of field '" + fieldName + "' requires a non-blank value");
        }
        this.correctedValue = newValue;   // extractedValue is preserved, never overwritten
        settle(FieldDecision.CORRECTED, null, reviewer);
    }

    void reject(String reason, Reviewer reviewer) {
        if (reason == null || reason.isBlank()) {
            throw new ReviewInvariantViolationException(
                    "Rejecting field '" + fieldName + "' requires a rejection reason");
        }
        settle(FieldDecision.REJECTED, reason, reviewer);
    }

    void flagNeedsReview(Reviewer reviewer) {
        settle(FieldDecision.NEEDS_REVIEW, null, reviewer);
    }

    private void settle(FieldDecision decision, String rejectionReason, Reviewer reviewer) {
        this.decision = decision;
        this.rejectionReason = rejectionReason;
        this.reviewerId = reviewer.userId();
        this.reviewedAt = Instant.now();
    }

    // ---- Queries --------------------------------------------------------------------------------

    /** The value that stands after review: the correction if present, else the OCR output. */
    public String effectiveValue() {
        return correctedValue != null ? correctedValue : extractedValue;
    }

    /** Whether a human (or an allowed auto-accept) has settled this field — used for progress + completion. */
    public boolean isSettled() {
        return decision != FieldDecision.NEEDS_REVIEW;
    }

    public FieldId fieldId()                 { return fieldId; }
    public String fieldName()                { return fieldName; }
    public String displayLabel()             { return displayLabel; }
    public String extractedValue()           { return extractedValue; }
    public String correctedValue()           { return correctedValue; }
    public double confidence()               { return confidence; }
    public ConfidenceLevel confidenceLevel() { return confidenceLevel; }
    public BoundingBox boundingBox()         { return boundingBox; }
    public int sortOrder()                   { return sortOrder; }
    public FieldDecision decision()          { return decision; }
    public String rejectionReason()          { return rejectionReason; }
    public java.util.UUID reviewerId()       { return reviewerId; }
    public Instant reviewedAt()              { return reviewedAt; }
}
