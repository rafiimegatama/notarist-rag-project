package com.notarist.kase.domain.state;

/**
 * The nature of a transition. Recorded on every move so that "how often do drafts get sent back, and
 * by whom?" is a query rather than a state-diff reconstruction.
 *
 * <p>ROLLBACK and CANCEL <b>require a reason</b>; the aggregate rejects them without one.
 */
public enum TransitionKind {

    /** Normal progress along the workflow. */
    FORWARD,

    /** Re-enter the same stage after a failure (OCR_FAILED → OCR_RUNNING). */
    RETRY,

    /** Move backwards to an earlier human stage. Reason mandatory. Nothing is destroyed. */
    ROLLBACK,

    /** Abandon the case. Reason mandatory. Only before a notary is involved. */
    CANCEL;

    public boolean requiresReason() {
        return this == ROLLBACK || this == CANCEL;
    }
}
