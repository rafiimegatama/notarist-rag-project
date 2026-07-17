package com.notarist.verification.application.port.in;

import com.notarist.verification.api.response.VerificationResponse;
import com.notarist.verification.application.command.InitializeVerificationCommand;

/**
 * Provisions a verification for a bundle from OCR-review output plus the manual observation items. Not
 * exposed over REST — a future bundle-ready listener (or a test) drives it. Kept as a use-case port so
 * nothing reaches into the aggregate directly.
 */
public interface VerificationProvisioningUseCase {

    VerificationResponse initializeVerification(InitializeVerificationCommand command);
}
