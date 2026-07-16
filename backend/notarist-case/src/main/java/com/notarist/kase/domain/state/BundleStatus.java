package com.notarist.kase.domain.state;

/** A bundle's composition lifecycle. */
public enum BundleStatus {

    /** Accepting documents. */
    OPEN,

    /** Expected document count met. Can still be reopened by detaching a document. */
    COMPLETE,

    /**
     * Sealed. Terminal, and there is no unlock operation at any privilege level.
     *
     * <p>The notary signed on the basis of these exact documents. Swapping one afterwards would
     * silently invalidate the evidentiary chain, so a correction requires a NEW bundle — recorded as
     * such — rather than an edit that leaves no trace.
     */
    LOCKED;

    public boolean isTerminal() {
        return this == LOCKED;
    }

    public boolean acceptsDocuments() {
        return this == OPEN || this == COMPLETE;
    }
}
