package com.notarist.kase.domain.state;

/**
 * A Timeline is append-only, so its "state machine" governs whether it still accepts entries.
 *
 * <p>ACTIVE while the case is live; SEALED once the case reaches a terminal state. Sealing is what
 * makes the record evidentially useful: after the case is archived, the story of what happened can no
 * longer grow a new chapter.
 */
public enum TimelineStatus {

    ACTIVE,
    SEALED;

    public boolean isTerminal() {
        return this == SEALED;
    }

    public boolean acceptsEntries() {
        return this == ACTIVE;
    }
}
