package com.notarist.kase.application.command;

import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.BundleId;

/** Request to move a bundle to {@code targetStatus}. Legality is decided by the aggregate. */
public record ChangeBundleStatusCommand(
        BundleId bundleId,
        BundleWorkflowStatus targetStatus,
        CallerContext caller
) {
    public ChangeBundleStatusCommand {
        if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
        if (targetStatus == null) throw new IllegalArgumentException("targetStatus is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
