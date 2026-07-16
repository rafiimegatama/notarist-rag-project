package com.notarist.review.domain.valueobject;

/**
 * The kinds of authority extraction the review panel surfaces. These are read-only OCR outputs — a
 * reviewer may confirm or reject them, never edit them.
 */
public enum AuthorityType {
    AUTHORITY_CLAUSE,
    DIRECTOR_TIMELINE,
    CURRENT_DIRECTORS,
    SIGNING_AUTHORITY
}
