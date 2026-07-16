package com.notarist.kase.domain.exception;

/** An aggregate was asked to enter a state its own invariants forbid. */
public class InvariantViolationException extends RuntimeException {
    public InvariantViolationException(String message) {
        super(message);
    }
}
