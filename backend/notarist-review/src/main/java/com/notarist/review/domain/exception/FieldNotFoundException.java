package com.notarist.review.domain.exception;

/** The referenced field does not belong to this review. */
public class FieldNotFoundException extends RuntimeException {
    public FieldNotFoundException(String message) {
        super(message);
    }
}
