package com.notarist.verification.domain.valueobject;

import java.util.UUID;

/** Identity of a single {@code ChecklistItem} within a verification. */
public record ItemId(UUID value) {

    public ItemId {
        if (value == null) throw new IllegalArgumentException("itemId is required");
    }

    public static ItemId of(UUID value) {
        return new ItemId(value);
    }

    public static ItemId generate() {
        return new ItemId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
