package com.notarist.core.domain.valueobject;

/** Data classification level — controls visibility and access policy in VPD. */
public enum ClassificationLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    STRICTLY_CONFIDENTIAL;

    /** Returns true if this level is strictly more sensitive than {@code other}. */
    public boolean exceeds(ClassificationLevel other) {
        return this.ordinal() > other.ordinal();
    }

    /** Returns true if this level is at least as sensitive as {@code other}. */
    public boolean isAtLeast(ClassificationLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
