package com.notarist.verification.domain.valueobject;

import java.util.UUID;

/** Identity of a {@code Verification} aggregate. */
public record VerificationId(UUID value) {

    public VerificationId {
        if (value == null) throw new IllegalArgumentException("verificationId is required");
    }

    public static VerificationId of(UUID value) {
        return new VerificationId(value);
    }

    public static VerificationId generate() {
        return new VerificationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
