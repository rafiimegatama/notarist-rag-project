package com.notarist.verification.domain.exception;

/** No verification exists for the requested bundle/id (or it belongs to another tenant — indistinguishable). */
public class VerificationNotFoundException extends RuntimeException {
    public VerificationNotFoundException(String message) {
        super(message);
    }
}
