package com.notarist.review.domain.state;

/**
 * The banded confidence of an OCR extraction. Derived from the raw numeric score, never set by hand,
 * so the band and the number can never disagree.
 *
 * <p>Thresholds: HIGH ≥ 0.85, MEDIUM ≥ 0.60, otherwise LOW. HIGH may be accepted without edit; LOW
 * must go through a human decision (see the business rules enforced by the aggregate).
 */
public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    public static final double HIGH_THRESHOLD = 0.85;
    public static final double MEDIUM_THRESHOLD = 0.60;

    public static ConfidenceLevel from(double confidence) {
        if (confidence >= HIGH_THRESHOLD) return HIGH;
        if (confidence >= MEDIUM_THRESHOLD) return MEDIUM;
        return LOW;
    }
}
