package com.notarist.verification.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA row for one append-only checklist-decision audit line. Never updated, never deleted (except by
 * the parent verification's cascade). The UNIQUE (verification_id, sequence) constraint in the schema
 * is the append concurrency guard.
 */
@Entity
@Table(name = "verification_item_audit")
public class VerificationItemAuditJpaEntity {

    @Id
    @Column(name = "audit_id", length = 36, nullable = false)
    private String auditId;

    @Column(name = "verification_id", length = 36, nullable = false)
    private String verificationId;

    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @Column(name = "decision", length = 30, nullable = false)
    private String decision;

    @Column(name = "previous_decision", length = 30)
    private String previousDecision;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "reviewer_id", length = 36, nullable = false)
    private String reviewerId;

    @Column(name = "reviewer_role", length = 50, nullable = false)
    private String reviewerRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    protected VerificationItemAuditJpaEntity() {}

    public VerificationItemAuditJpaEntity(String auditId, String verificationId, String itemId,
                                          String decision, String previousDecision, String comment,
                                          String reviewerId, String reviewerRole, Instant occurredAt,
                                          int sequence) {
        this.auditId = auditId;
        this.verificationId = verificationId;
        this.itemId = itemId;
        this.decision = decision;
        this.previousDecision = previousDecision;
        this.comment = comment;
        this.reviewerId = reviewerId;
        this.reviewerRole = reviewerRole;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    public String getAuditId()           { return auditId; }
    public String getVerificationId()    { return verificationId; }
    public String getItemId()            { return itemId; }
    public String getDecision()          { return decision; }
    public String getPreviousDecision()  { return previousDecision; }
    public String getComment()           { return comment; }
    public String getReviewerId()        { return reviewerId; }
    public String getReviewerRole()      { return reviewerRole; }
    public Instant getOccurredAt()       { return occurredAt; }
    public int getSequence()             { return sequence; }
}
