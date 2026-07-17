package com.notarist.review.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.review.domain.valueobject.ReviewId;

import java.util.UUID;

/** Raised when a review reaches VERIFIED — the terminal, human-signed-off state. */
public final class ReviewVerified extends ReviewDomainEvent {

    private final ReviewId reviewId;
    private final UUID documentId;
    private final UUID tenantId;
    private final UUID reviewerId;

    public ReviewVerified(ReviewId reviewId, UUID documentId, UUID tenantId, UUID reviewerId,
                          CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.reviewId = reviewId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.reviewerId = reviewerId;
    }

    @Override public String eventType() { return "OCR_REVIEW_VERIFIED"; }

    public ReviewId reviewId() { return reviewId; }
    public UUID documentId()   { return documentId; }
    public UUID tenantId()     { return tenantId; }
    public UUID reviewerId()   { return reviewerId; }
}
