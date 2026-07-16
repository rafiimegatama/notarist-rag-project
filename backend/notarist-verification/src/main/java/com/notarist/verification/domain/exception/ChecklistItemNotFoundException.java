package com.notarist.verification.domain.exception;

/** The referenced checklist item does not belong to this verification. */
public class ChecklistItemNotFoundException extends RuntimeException {
    public ChecklistItemNotFoundException(String message) {
        super(message);
    }
}
