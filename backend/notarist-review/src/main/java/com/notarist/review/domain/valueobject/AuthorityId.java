package com.notarist.review.domain.valueobject;

import java.util.UUID;

/** Identity of a single {@code AuthorityItem} within a review. */
public record AuthorityId(UUID value) {

    public AuthorityId {
        if (value == null) throw new IllegalArgumentException("authorityId is required");
    }

    public static AuthorityId of(UUID value) {
        return new AuthorityId(value);
    }

    public static AuthorityId generate() {
        return new AuthorityId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
