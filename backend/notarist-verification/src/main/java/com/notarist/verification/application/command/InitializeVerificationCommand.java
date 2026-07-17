package com.notarist.verification.application.command;

import com.notarist.verification.application.query.CallerContext;

import java.util.UUID;

/**
 * Provisions a verification for a bundle: builds the automatic checks from OCR-review output (via the
 * facts port) plus the manual observation items, all PENDING/pre-filled. Not exposed as a REST
 * endpoint — a future bundle-ready listener (or a test) drives it.
 */
public record InitializeVerificationCommand(
        UUID bundleId,
        UUID tenantId,
        CallerContext caller
) {
    public InitializeVerificationCommand {
        if (bundleId == null) throw new IllegalArgumentException("bundleId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
    }
}
