package com.notarist.review.domain.state;

/**
 * The lifecycle of a document's OCR review.
 *
 * <pre>
 *   PENDING → IN_PROGRESS → REVIEW_COMPLETED → VERIFIED
 * </pre>
 *
 * <p>Strictly forward. The legal edges live in {@link ReviewStatusMachine}; anything not listed there
 * is unreachable — the aggregate has no public status setter, so an illegal transition is not "caught
 * by validation", it simply cannot be expressed. VERIFIED is terminal.
 */
public enum ReviewStatus {
    PENDING,
    IN_PROGRESS,
    REVIEW_COMPLETED,
    VERIFIED;

    public boolean isTerminal() {
        return this == VERIFIED;
    }
}
