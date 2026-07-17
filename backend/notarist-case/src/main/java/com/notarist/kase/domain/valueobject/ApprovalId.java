package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/** Immutable identity. */
public record ApprovalId(UUID value) {

    public ApprovalId {
        if (value == null) throw new IllegalArgumentException("ApprovalId value must not be null");
    }

    public static ApprovalId generate() {
        return new ApprovalId(UUID.randomUUID());
    }

    public static ApprovalId of(UUID value) {
        return new ApprovalId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
