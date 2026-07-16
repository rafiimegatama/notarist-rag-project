package com.notarist.kase.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.kase.domain.state.BundleWorkflowStatus.*;

/**
 * The authoritative bundle-workflow transition table. Consulted only from inside the
 * {@code BundleWorkflow} aggregate — an application service requests a transition, it never asks this
 * table "is it legal?" and then mutates.
 *
 * <p>Shape: a mostly-linear pipeline with two rework loops. QC can fail and send the bundle back to
 * re-verify or re-QC; everything else moves forward. LOCKED is terminal.
 */
public final class BundleWorkflowStateMachine {

    private BundleWorkflowStateMachine() {}

    private static final Map<BundleWorkflowStatus, Set<BundleWorkflowStatus>> TABLE = build();

    private static Map<BundleWorkflowStatus, Set<BundleWorkflowStatus>> build() {
        Map<BundleWorkflowStatus, Set<BundleWorkflowStatus>> t = new EnumMap<>(BundleWorkflowStatus.class);

        t.put(OPEN,                   Set.of(COLLECTING_DOCUMENTS));
        t.put(COLLECTING_DOCUMENTS,   Set.of(READY_FOR_VERIFICATION, OPEN));
        t.put(READY_FOR_VERIFICATION, Set.of(UNDER_VERIFICATION, COLLECTING_DOCUMENTS));
        t.put(UNDER_VERIFICATION,     Set.of(READY_FOR_QC, COLLECTING_DOCUMENTS));
        t.put(READY_FOR_QC,           Set.of(QC_PASSED, QC_FAILED));
        // QC failed — rework: re-run QC, or go back to re-verify the source facts.
        t.put(QC_FAILED,              Set.of(READY_FOR_QC, READY_FOR_VERIFICATION));
        t.put(QC_PASSED,              Set.of(READY_FOR_DELIVERY));
        t.put(READY_FOR_DELIVERY,     Set.of(DELIVERED));
        t.put(DELIVERED,              Set.of(LOCKED));
        t.put(LOCKED,                 Set.of());   // terminal — irreversible

        return t;
    }

    public static boolean isLegal(BundleWorkflowStatus from, BundleWorkflowStatus to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<BundleWorkflowStatus> allowedTargets(BundleWorkflowStatus from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
