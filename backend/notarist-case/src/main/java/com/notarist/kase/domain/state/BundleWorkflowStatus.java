package com.notarist.kase.domain.state;

import java.util.Set;

/**
 * The PROCESS lifecycle of a bundle — distinct from {@link BundleStatus}, which tracks document
 * <em>composition</em> (OPEN/COMPLETE/LOCKED). A bundle assembles documents (BundleStatus) and, in
 * parallel, moves through this verification → QC → delivery workflow.
 *
 * <p>Kept separate from the composition status on purpose: "are all the documents here?" and "where is
 * this bundle in the office process?" are different questions with different owners, and folding them
 * into one enum is how a status field ends up meaning two things at once.
 */
public enum BundleWorkflowStatus {

    OPEN,
    COLLECTING_DOCUMENTS,
    READY_FOR_VERIFICATION,
    UNDER_VERIFICATION,
    READY_FOR_QC,
    QC_FAILED,
    QC_PASSED,
    READY_FOR_DELIVERY,
    DELIVERED,
    LOCKED;

    private static final Set<BundleWorkflowStatus> TERMINAL = Set.of(LOCKED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** The bundle has cleared QC and may proceed toward delivery. */
    public boolean hasPassedQc() {
        return this == QC_PASSED || this == READY_FOR_DELIVERY || this == DELIVERED || this == LOCKED;
    }
}
