package com.notarist.verification.application.command;

import com.notarist.verification.application.query.CallerContext;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.valueobject.BundleId;

/** Request a verification-status transition. Legality is decided by the aggregate. */
public record ChangeVerificationStatusCommand(
        BundleId bundleId,
        VerificationStatus targetStatus,
        CallerContext caller
) {
    public ChangeVerificationStatusCommand {
        if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
        if (targetStatus == null) throw new IllegalArgumentException("targetStatus is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
