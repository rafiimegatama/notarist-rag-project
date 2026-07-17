package com.notarist.review.application.command;

import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.valueobject.DocumentId;

/** Request a review-status transition. Legality is decided by the aggregate. */
public record ChangeReviewStatusCommand(
        DocumentId documentId,
        ReviewStatus targetStatus,
        CallerContext caller
) {
    public ChangeReviewStatusCommand {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (targetStatus == null) throw new IllegalArgumentException("targetStatus is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
