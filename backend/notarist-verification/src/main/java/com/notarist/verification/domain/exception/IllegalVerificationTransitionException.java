package com.notarist.verification.domain.exception;

import com.notarist.verification.domain.state.VerificationStatus;

/** A requested verification-status transition is not in the state machine. */
public class IllegalVerificationTransitionException extends RuntimeException {

    public IllegalVerificationTransitionException(String message) {
        super(message);
    }

    public static IllegalVerificationTransitionException of(VerificationStatus from, VerificationStatus to) {
        return new IllegalVerificationTransitionException(
                "Illegal verification transition " + from + " → " + to);
    }
}
