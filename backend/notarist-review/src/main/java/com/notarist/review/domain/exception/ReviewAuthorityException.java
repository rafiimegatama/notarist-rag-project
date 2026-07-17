package com.notarist.review.domain.exception;

/** The actor's role may not perform the requested review action. */
public class ReviewAuthorityException extends RuntimeException {
    public ReviewAuthorityException(String message) {
        super(message);
    }
}
