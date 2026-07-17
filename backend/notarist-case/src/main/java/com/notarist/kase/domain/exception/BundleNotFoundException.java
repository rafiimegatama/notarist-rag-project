package com.notarist.kase.domain.exception;

/**
 * A bundle could not be found for the caller. Also raised for a cross-tenant reference — "does not
 * exist" and "exists, but not in your tenant" must be indistinguishable, so both map to 404.
 */
public class BundleNotFoundException extends RuntimeException {
    public BundleNotFoundException(String message) {
        super(message);
    }
}
