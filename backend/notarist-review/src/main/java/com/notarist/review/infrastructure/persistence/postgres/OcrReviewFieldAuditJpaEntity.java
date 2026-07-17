package com.notarist.review.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA row for one append-only field-decision audit line. Never updated, never deleted (except by the
 * parent review's cascade). The UNIQUE (review_id, sequence) constraint in the schema is the append
 * concurrency guard.
 */
@Entity
@Table(name = "ocr_review_field_audit")
public class OcrReviewFieldAuditJpaEntity {

    @Id
    @Column(name = "audit_id", length = 36, nullable = false)
    private String auditId;

    @Column(name = "review_id", length = 36, nullable = false)
    private String reviewId;

    @Column(name = "field_id", length = 36, nullable = false)
    private String fieldId;

    @Column(name = "decision", length = 30, nullable = false)
    private String decision;

    @Column(name = "previous_value", length = 4000)
    private String previousValue;

    @Column(name = "new_value", length = 4000)
    private String newValue;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "reviewer_id", length = 36, nullable = false)
    private String reviewerId;

    @Column(name = "reviewer_role", length = 50, nullable = false)
    private String reviewerRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    protected OcrReviewFieldAuditJpaEntity() {}

    public OcrReviewFieldAuditJpaEntity(String auditId, String reviewId, String fieldId, String decision,
                                        String previousValue, String newValue, String reason,
                                        String reviewerId, String reviewerRole, Instant occurredAt,
                                        int sequence) {
        this.auditId = auditId;
        this.reviewId = reviewId;
        this.fieldId = fieldId;
        this.decision = decision;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.reason = reason;
        this.reviewerId = reviewerId;
        this.reviewerRole = reviewerRole;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    public String getAuditId()        { return auditId; }
    public String getReviewId()       { return reviewId; }
    public String getFieldId()        { return fieldId; }
    public String getDecision()       { return decision; }
    public String getPreviousValue()  { return previousValue; }
    public String getNewValue()       { return newValue; }
    public String getReason()         { return reason; }
    public String getReviewerId()     { return reviewerId; }
    public String getReviewerRole()   { return reviewerRole; }
    public Instant getOccurredAt()    { return occurredAt; }
    public int getSequence()          { return sequence; }
}
