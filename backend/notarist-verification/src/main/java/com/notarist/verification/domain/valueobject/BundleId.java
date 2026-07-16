package com.notarist.verification.domain.valueobject;

import java.util.UUID;

/**
 * Identity of the verified bundle. Owned by the Case/Bundle context; carried here by value so the
 * Verification context need not depend on that module.
 */
public record BundleId(UUID value) {

    public BundleId {
        if (value == null) throw new IllegalArgumentException("bundleId is required");
    }

    public static BundleId of(UUID value) {
        return new BundleId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
