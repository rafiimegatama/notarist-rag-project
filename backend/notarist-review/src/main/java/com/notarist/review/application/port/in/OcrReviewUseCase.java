package com.notarist.review.application.port.in;

import com.notarist.review.api.response.OcrReviewResponse;
import com.notarist.review.api.response.OcrReviewSummaryResponse;
import com.notarist.review.application.command.ChangeReviewStatusCommand;
import com.notarist.review.application.command.ReviewFieldCommand;
import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.valueobject.DocumentId;

/** The OCR review use cases behind the four REST endpoints. */
public interface OcrReviewUseCase {

    /** The full review payload for a document (fields, bounding boxes, confidence, authority timeline). */
    OcrReviewResponse getReview(DocumentId documentId, CallerContext caller);

    /** Progress-only view: accepted / corrected / rejected / remaining. */
    OcrReviewSummaryResponse getSummary(DocumentId documentId, CallerContext caller);

    /** Record a reviewer's decision on one field. Returns the refreshed review. */
    OcrReviewResponse reviewField(ReviewFieldCommand command);

    /** Move the review through its lifecycle. Legality is decided by the aggregate. */
    OcrReviewResponse changeStatus(ChangeReviewStatusCommand command);
}
