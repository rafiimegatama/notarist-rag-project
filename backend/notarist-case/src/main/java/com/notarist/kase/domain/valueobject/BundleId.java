package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record BundleId(UUID value) {

    public BundleId {
        if (value == null) throw new IllegalArgumentException("BundleId value must not be null");
    }

    public static BundleId generate() {
        return new BundleId(UUID.randomUUID());
    }

    public static BundleId of(UUID value) {
        return new BundleId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
