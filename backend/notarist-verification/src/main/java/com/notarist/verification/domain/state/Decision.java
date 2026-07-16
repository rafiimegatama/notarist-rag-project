package com.notarist.verification.domain.state;

/**
 * The verifier's decision on a single checklist item.
 *
 * <ul>
 *   <li>{@code PASS}            — the check is satisfied.</li>
 *   <li>{@code FAIL}            — the check is not satisfied; a reason (comment) is mandatory.</li>
 *   <li>{@code NOT_APPLICABLE} — the check does not apply to this bundle.</li>
 *   <li>{@code MANUAL_REQUIRED} — needs a human look; blocks completion until resolved.</li>
 * </ul>
 */
public enum Decision {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    MANUAL_REQUIRED;

    /** A mandatory item may only stand in the way of VERIFIED unless it is PASS or NOT_APPLICABLE. */
    public boolean isAcceptable() {
        return this == PASS || this == NOT_APPLICABLE;
    }

    public boolean isBlocking() {
        return this == MANUAL_REQUIRED;
    }
}
