package com.notarist.review.domain.valueobject;

import java.util.UUID;

/** Identity of a single {@code FieldReview} within a review. */
public record FieldId(UUID value) {

    public FieldId {
        if (value == null) throw new IllegalArgumentException("fieldId is required");
    }

    public static FieldId of(UUID value) {
        return new FieldId(value);
    }

    public static FieldId generate() {
        return new FieldId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
