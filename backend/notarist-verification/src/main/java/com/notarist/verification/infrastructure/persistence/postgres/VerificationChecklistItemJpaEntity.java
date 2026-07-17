package com.notarist.verification.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA row for one checklist item — a child of {@link VerificationJpaEntity}. The category/title/type
 * are fixed at birth; only the verifier's decision (status, decision, reviewer_id, reviewed_at,
 * comment) is mutable.
 */
@Entity
@Table(name = "verification_checklist_item")
public class VerificationChecklistItemJpaEntity {

    @Id
    @Column(name = "item_id", length = 36, nullable = false)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private VerificationJpaEntity verification;

    @Column(name = "category", length = 40, nullable = false)
    private String category;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "check_type", length = 20, nullable = false)
    private String checkType;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "decision", length = 30)
    private String decision;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected VerificationChecklistItemJpaEntity() {}

    public VerificationChecklistItemJpaEntity(String itemId, String category, String title,
                                              boolean mandatory, String checkType, String status,
                                              String decision, String reviewerId, Instant reviewedAt,
                                              String comment, int sortOrder) {
        this.itemId = itemId;
        this.category = category;
        this.title = title;
        this.mandatory = mandatory;
        this.checkType = checkType;
        this.status = status;
        this.decision = decision;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.comment = comment;
        this.sortOrder = sortOrder;
    }

    void setVerification(VerificationJpaEntity verification) { this.verification = verification; }

    public String getItemId()          { return itemId; }
    public VerificationJpaEntity getVerification() { return verification; }
    public String getCategory()        { return category; }
    public String getTitle()           { return title; }
    public boolean isMandatory()       { return mandatory; }
    public String getCheckType()       { return checkType; }
    public String getStatus()          { return status; }
    public String getDecision()        { return decision; }
    public String getReviewerId()      { return reviewerId; }
    public Instant getReviewedAt()     { return reviewedAt; }
    public String getComment()         { return comment; }
    public int getSortOrder()          { return sortOrder; }
    public int getVersion()            { return version; }

    // Mutable decision columns only.
    public void setStatus(String status)          { this.status = status; }
    public void setDecision(String decision)      { this.decision = decision; }
    public void setReviewerId(String reviewerId)  { this.reviewerId = reviewerId; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public void setComment(String comment)        { this.comment = comment; }
}
