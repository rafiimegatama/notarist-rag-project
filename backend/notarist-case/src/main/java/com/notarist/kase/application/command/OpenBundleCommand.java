package com.notarist.kase.application.command;

import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.valueobject.BundleType;
import com.notarist.kase.domain.valueobject.CaseId;

/** Request to open a new bundle on a case. */
public record OpenBundleCommand(
        CaseId caseId,
        BundleType bundleType,
        int expectedDocumentCount,
        CallerContext caller
) {
    public OpenBundleCommand {
        if (caseId == null) throw new IllegalArgumentException("caseId is required");
        if (bundleType == null) throw new IllegalArgumentException("bundleType is required");
        if (expectedDocumentCount < 0) throw new IllegalArgumentException("expectedDocumentCount must be >= 0");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
