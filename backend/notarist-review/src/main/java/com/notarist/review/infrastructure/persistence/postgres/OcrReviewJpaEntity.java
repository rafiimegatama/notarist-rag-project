package com.notarist.review.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA row for the {@link com.notarist.review.domain.model.OcrReview} aggregate root.
 *
 * <p>Only the review-progress columns are mutable (review_status, reviewer_id, reviewed_at,
 * last_audit_sequence) plus the field/authority children. Mutation flows through
 * {@code OcrReviewRepositoryImpl.save}, which loads the managed entity and copies the new values in,
 * so Hibernate's dirty checking and the {@link Version} column enforce optimistic locking rather than
 * a blind overwrite. Every field decision stamps reviewer_id/reviewed_at here, so the root is dirtied
 * and its version bumps — that is what guards two reviewers editing the same document concurrently.
 */
@Entity
@Table(name = "ocr_review")
public class OcrReviewJpaEntity {

    @Id
    @Column(name = "review_id", length = 36, nullable = false)
    private String reviewId;

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "review_status", length = 50, nullable = false)
    private String reviewStatus;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "document_name", length = 500, nullable = false)
    private String documentName;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "stamp_detected", nullable = false)
    private boolean stampDetected;

    @Column(name = "signature_detected", nullable = false)
    private boolean signatureDetected;

    @Column(name = "overall_confidence", nullable = false)
    private double overallConfidence;

    @Column(name = "last_audit_sequence", nullable = false)
    private int lastAuditSequence;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OcrReviewFieldJpaEntity> fields = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OcrAuthorityItemJpaEntity> authorityItems = new ArrayList<>();

    protected OcrReviewJpaEntity() {}

    public OcrReviewJpaEntity(String reviewId, String documentId, String tenantId, String reviewStatus,
                              String reviewerId, Instant reviewedAt, String documentName, int pageCount,
                              boolean stampDetected, boolean signatureDetected, double overallConfidence,
                              int lastAuditSequence, Instant createdAt) {
        this.reviewId = reviewId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.reviewStatus = reviewStatus;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.documentName = documentName;
        this.pageCount = pageCount;
        this.stampDetected = stampDetected;
        this.signatureDetected = signatureDetected;
        this.overallConfidence = overallConfidence;
        this.lastAuditSequence = lastAuditSequence;
        this.createdAt = createdAt;
    }

    public void addField(OcrReviewFieldJpaEntity field) {
        field.setReview(this);
        this.fields.add(field);
    }

    public void addAuthorityItem(OcrAuthorityItemJpaEntity item) {
        item.setReview(this);
        this.authorityItems.add(item);
    }

    public String getReviewId()            { return reviewId; }
    public String getDocumentId()          { return documentId; }
    public String getTenantId()            { return tenantId; }
    public String getReviewStatus()        { return reviewStatus; }
    public String getReviewerId()          { return reviewerId; }
    public Instant getReviewedAt()         { return reviewedAt; }
    public String getDocumentName()        { return documentName; }
    public int getPageCount()              { return pageCount; }
    public boolean isStampDetected()       { return stampDetected; }
    public boolean isSignatureDetected()   { return signatureDetected; }
    public double getOverallConfidence()   { return overallConfidence; }
    public int getLastAuditSequence()      { return lastAuditSequence; }
    public int getVersion()                { return version; }
    public Instant getCreatedAt()          { return createdAt; }
    public List<OcrReviewFieldJpaEntity> getFields()          { return fields; }
    public List<OcrAuthorityItemJpaEntity> getAuthorityItems() { return authorityItems; }

    // Only the review-progress columns carry setters — everything else is fixed at birth.
    public void setReviewStatus(String reviewStatus)      { this.reviewStatus = reviewStatus; }
    public void setReviewerId(String reviewerId)          { this.reviewerId = reviewerId; }
    public void setReviewedAt(Instant reviewedAt)         { this.reviewedAt = reviewedAt; }
    public void setLastAuditSequence(int lastAuditSequence) { this.lastAuditSequence = lastAuditSequence; }
}
