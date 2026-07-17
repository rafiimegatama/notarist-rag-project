package com.notarist.review.domain.state;

/**
 * The reviewer's decision on a single extracted field.
 *
 * <ul>
 *   <li>{@code AUTO_ACCEPTED}   — accepted without edit; only legal for HIGH confidence.</li>
 *   <li>{@code MANUAL_ACCEPTED} — a human accepted the extracted value as-is.</li>
 *   <li>{@code CORRECTED}       — a human replaced the value; the original is preserved.</li>
 *   <li>{@code REJECTED}        — the value is wrong/unusable; a reason is mandatory.</li>
 *   <li>{@code NEEDS_REVIEW}    — not yet decided / flagged for a human to look again.</li>
 * </ul>
 */
public enum FieldDecision {
    AUTO_ACCEPTED,
    MANUAL_ACCEPTED,
    CORRECTED,
    REJECTED,
    NEEDS_REVIEW;

    /** A field is "settled" once a human (or an allowed auto-accept) has accepted or corrected it. */
    public boolean isAccepted() {
        return this == AUTO_ACCEPTED || this == MANUAL_ACCEPTED || this == CORRECTED;
    }
}
