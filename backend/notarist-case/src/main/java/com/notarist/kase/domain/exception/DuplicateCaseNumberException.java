package com.notarist.kase.domain.exception;

/** A case number that already exists for the tenant was submitted. Maps to 409 Conflict. */
public class DuplicateCaseNumberException extends RuntimeException {
    public DuplicateCaseNumberException(String message) {
        super(message);
    }
}
