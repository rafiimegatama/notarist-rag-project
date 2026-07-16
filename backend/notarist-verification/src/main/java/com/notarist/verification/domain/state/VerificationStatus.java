package com.notarist.verification.domain.state;

/**
 * The lifecycle of a bundle's verification.
 *
 * <pre>
 *   PENDING → UNDER_VERIFICATION → VERIFIED
 *                               ↘ FAILED
 *   VERIFIED / FAILED → UNDER_VERIFICATION   (returned for rework)
 * </pre>
 *
 * <p>The legal edges live in {@link VerificationStatusMachine}; anything not listed there is
 * unreachable — the aggregate has no public status setter, so an illegal transition cannot even be
 * expressed.
 */
public enum VerificationStatus {
    PENDING,
    UNDER_VERIFICATION,
    VERIFIED,
    FAILED;

    /** VERIFIED and FAILED are outcomes — reachable, and returnable for rework, but not "in flight". */
    public boolean isOutcome() {
        return this == VERIFIED || this == FAILED;
    }
}
