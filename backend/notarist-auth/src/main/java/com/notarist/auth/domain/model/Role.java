package com.notarist.auth.domain.model;

import com.notarist.core.domain.valueobject.ClassificationLevel;

/** User roles in NOTARIST RAG Platform — governs API access and data clearance. */
public enum Role {

    STAFF(ClassificationLevel.INTERNAL),
    NOTARIS(ClassificationLevel.CONFIDENTIAL),
    PPAT_OFFICER(ClassificationLevel.CONFIDENTIAL),
    PIMPINAN(ClassificationLevel.STRICTLY_CONFIDENTIAL),
    ADMIN(ClassificationLevel.STRICTLY_CONFIDENTIAL);

    private final ClassificationLevel defaultClearance;

    Role(ClassificationLevel defaultClearance) {
        this.defaultClearance = defaultClearance;
    }

    public ClassificationLevel getDefaultClearance() {
        return defaultClearance;
    }

    public boolean hasAtLeastClearance(ClassificationLevel required) {
        return defaultClearance.isAtLeast(required);
    }
}
