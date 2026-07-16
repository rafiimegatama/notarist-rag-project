package com.notarist.kase.domain.valueobject;

import java.util.UUID;

/**
 * Who is performing an act, and with what authority.
 *
 * <p>Passed into every transition so the aggregate can enforce authority itself and record who did
 * it. A transition with no actor is not permitted — except for {@link #system()}, which names the
 * background worker explicitly rather than leaving the actor null.
 */
public record Actor(UUID userId, Role role) {

    private static final UUID SYSTEM_ID = new UUID(0L, 0L);

    public Actor {
        if (role == null) throw new IllegalArgumentException("Actor role must not be null");
        if (userId == null && role != Role.SYSTEM) {
            throw new IllegalArgumentException("Actor userId must not be null for a human actor");
        }
    }

    public static Actor of(UUID userId, Role role) {
        return new Actor(userId, role);
    }

    /** The pipeline / scheduler. Used for automatic transitions; can never approve or verify. */
    public static Actor system() {
        return new Actor(SYSTEM_ID, Role.SYSTEM);
    }

    public boolean isSystem() {
        return role == Role.SYSTEM;
    }

    public boolean isHuman() {
        return role != Role.SYSTEM;
    }

    public boolean hasRole(Role... allowed) {
        for (Role r : allowed) if (r == role) return true;
        return false;
    }
}
