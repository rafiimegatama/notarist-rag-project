package com.notarist.kase.domain.valueobject;

/**
 * Why a transition happened.
 *
 * <p>Mandatory for ROLLBACK and CANCEL (enforced by the aggregate). A notary must be able to show a
 * regulator <em>why</em> a case was sent back or abandoned; "someone changed a field" is not an
 * answer. Optional for forward transitions, where the transition itself is the explanation.
 */
public record TransitionReason(String value) {

    public static final TransitionReason NONE = new TransitionReason(null);

    public TransitionReason {
        if (value != null && value.isBlank()) value = null;
    }

    public static TransitionReason of(String value) {
        return new TransitionReason(value);
    }

    public boolean isPresent() {
        return value != null;
    }
}
