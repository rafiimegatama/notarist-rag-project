package com.notarist.review.domain.exception;

import com.notarist.review.domain.state.ReviewStatus;

/** A requested review-status transition is not in the state machine. */
public class IllegalReviewTransitionException extends RuntimeException {

    public IllegalReviewTransitionException(String message) {
        super(message);
    }

    public static IllegalReviewTransitionException of(ReviewStatus from, ReviewStatus to) {
        return new IllegalReviewTransitionException(
                "Illegal review transition " + from + " → " + to);
    }
}
