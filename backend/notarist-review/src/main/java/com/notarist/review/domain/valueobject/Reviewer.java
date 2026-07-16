package com.notarist.review.domain.valueobject;

import java.util.UUID;

/** The human performing a review action — a user id and the role they act under. */
public record Reviewer(UUID userId, Role role) {

    public Reviewer {
        if (userId == null) throw new IllegalArgumentException("reviewer userId is required");
        if (role == null) throw new IllegalArgumentException("reviewer role is required");
    }

    public static Reviewer of(UUID userId, Role role) {
        return new Reviewer(userId, role);
    }

    public boolean isSystem() {
        return role == Role.SYSTEM;
    }
}
