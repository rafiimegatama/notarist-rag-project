package com.notarist.review.domain.exception;

/** No review exists for the requested document/id (or it belongs to another tenant — indistinguishable). */
public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(String message) {
        super(message);
    }
}
