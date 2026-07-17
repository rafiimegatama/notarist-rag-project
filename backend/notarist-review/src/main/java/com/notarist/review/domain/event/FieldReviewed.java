package com.notarist.review.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.ReviewId;

import java.util.UUID;

/** Raised whenever a reviewer records a decision on a single field. */
public final class FieldReviewed extends ReviewDomainEvent {

    private final ReviewId reviewId;
    private final UUID documentId;
    private final UUID tenantId;
    private final FieldId fieldId;
    private final String fieldName;
    private final FieldDecision decision;
    private final UUID reviewerId;

    public FieldReviewed(ReviewId reviewId, UUID documentId, UUID tenantId, FieldId fieldId,
                         String fieldName, FieldDecision decision, UUID reviewerId,
                         CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.reviewId = reviewId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.decision = decision;
        this.reviewerId = reviewerId;
    }

    @Override public String eventType() { return "OCR_FIELD_REVIEWED"; }

    public ReviewId reviewId()       { return reviewId; }
    public UUID documentId()         { return documentId; }
    public UUID tenantId()           { return tenantId; }
    public FieldId fieldId()         { return fieldId; }
    public String fieldName()        { return fieldName; }
    public FieldDecision decision()  { return decision; }
    public UUID reviewerId()         { return reviewerId; }
}
