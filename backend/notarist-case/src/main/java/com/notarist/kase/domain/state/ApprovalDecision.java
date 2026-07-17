package com.notarist.kase.domain.state;

/**
 * An approval's lifecycle. All non-PENDING states are terminal.
 *
 * <p>A decision is never reversed. Changing one's mind means raising a NEW approval, because the
 * original decision and its reversal are BOTH legally significant facts — erasing the first would
 * destroy the record of what the notary actually did, and when.
 */
public enum ApprovalDecision {

    PENDING,
    APPROVED,
    REJECTED,
    /** The decision window closed without an answer. */
    EXPIRED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
