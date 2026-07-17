package com.notarist.kase.domain.exception;

/**
 * The actor does not hold the authority required for this act.
 *
 * <p>Enforced inside the aggregate rather than at a controller, because a controller check is
 * bypassed the moment a second caller (an event listener, a batch job, a new endpoint) reaches the
 * same use case.
 */
public class AuthorityException extends RuntimeException {
    public AuthorityException(String message) {
        super(message);
    }
}
