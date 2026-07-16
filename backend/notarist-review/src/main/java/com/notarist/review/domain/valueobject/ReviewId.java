package com.notarist.review.domain.valueobject;

import java.util.UUID;

/** Identity of an {@code OcrReview} aggregate. */
public record ReviewId(UUID value) {

    public ReviewId {
        if (value == null) throw new IllegalArgumentException("reviewId is required");
    }

    public static ReviewId of(UUID value) {
        return new ReviewId(value);
    }

    public static ReviewId generate() {
        return new ReviewId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
