package com.notarist.kase.domain.exception;

/**
 * A case could not be found for the caller. Also raised for a cross-tenant reference — the caller
 * must not be able to tell "does not exist" apart from "exists, but not in your tenant", so both map
 * to the same 404.
 */
public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String message) {
        super(message);
    }
}
