package com.notarist.verification.application.port.out;

import com.notarist.verification.domain.service.VerificationFacts;
import com.notarist.verification.domain.valueobject.BundleId;

import java.util.UUID;

/**
 * Supplies the OCR-review output an automatic check consumes, for a given bundle. Declared as an
 * out-port so the Verification module never imports the Review/Case contexts — the composition root
 * provides the adapter. The default adapter returns empty facts (checks become MANUAL_REQUIRED), which
 * fails safe rather than silently passing.
 */
public interface VerificationFactsPort {

    VerificationFacts factsFor(BundleId bundleId, UUID tenantId);
}
