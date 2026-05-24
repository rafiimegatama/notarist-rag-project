package com.notarist.core.domain.valueobject;

/** Data classification levels — governs access control and field masking. */
public enum ClassificationLevel {

    /** Accessible to all authenticated users and potentially public. */
    PUBLIC(0),

    /** Internal use only — all authenticated staff. */
    INTERNAL(1),

    /** Restricted — NOTARIS, PPAT_OFFICER, PIMPINAN, ADMIN roles. */
    CONFIDENTIAL(2),

    /** Highest restriction — PIMPINAN, ADMIN, and designated NOTARIS only. */
    STRICTLY_CONFIDENTIAL(3);

    private final int level;

    ClassificationLevel(int level) {
        this.level = level;
    }

    public boolean isAtLeast(ClassificationLevel required) {
        return this.level >= required.level;
    }

    public boolean exceeds(ClassificationLevel clearance) {
        return this.level > clearance.level;
    }
}
