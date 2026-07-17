package com.notarist.review.domain.state;

/**
 * The reviewer's disposition of a read-only authority extraction. The extracted content itself is
 * never edited here — a reviewer may only confirm it or reject it.
 */
public enum AuthorityDecision {
    PENDING,
    CONFIRMED,
    REJECTED
}
