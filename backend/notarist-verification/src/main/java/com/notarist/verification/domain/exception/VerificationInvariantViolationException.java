package com.notarist.verification.domain.exception;

/** A business rule of the verification aggregate was violated (e.g. FAIL without a reason). */
public class VerificationInvariantViolationException extends RuntimeException {
    public VerificationInvariantViolationException(String message) {
        super(message);
    }
}
