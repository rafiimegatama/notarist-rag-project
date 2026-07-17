package com.notarist.review.domain.exception;

/** A business rule of the review aggregate was violated (e.g. REJECTED without a reason). */
public class ReviewInvariantViolationException extends RuntimeException {
    public ReviewInvariantViolationException(String message) {
        super(message);
    }
}
