package com.notarist.review.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;

import java.util.UUID;

/** Raised when a reviewer rejects a field. Carries the mandatory rejection reason. */
public final class ReviewRejected extends ReviewDomainEvent {

    private final ReviewId reviewId;
    private final UUID documentId;
    private final UUID tenantId;
    private final FieldId fieldId;
    private final String reason;
    private final UUID reviewerId;

    public ReviewRejected(ReviewId reviewId, UUID documentId, UUID tenantId, FieldId fieldId,
                          String reason, UUID reviewerId,
                          CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.reviewId = reviewId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.fieldId = fieldId;
        this.reason = reason;
        this.reviewerId = reviewerId;
    }

    @Override public String eventType() { return "OCR_REVIEW_REJECTED"; }

    public ReviewId reviewId() { return reviewId; }
    public UUID documentId()   { return documentId; }
    public UUID tenantId()     { return tenantId; }
    public FieldId fieldId()   { return fieldId; }
    public String reason()     { return reason; }
    public UUID reviewerId()   { return reviewerId; }
}
