package com.notarist.verification.domain.exception;

/** The actor's role may not perform the requested verification action. */
public class VerificationAuthorityException extends RuntimeException {
    public VerificationAuthorityException(String message) {
        super(message);
    }
}
