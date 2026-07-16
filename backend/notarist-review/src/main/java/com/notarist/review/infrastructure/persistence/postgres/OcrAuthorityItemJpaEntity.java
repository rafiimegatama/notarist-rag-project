package com.notarist.review.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA row for one authority extraction — a child of {@link OcrReviewJpaEntity}. The extracted content
 * is fixed at birth; only the reviewer's disposition (decision, decided_at) is mutable.
 */
@Entity
@Table(name = "ocr_authority_item")
public class OcrAuthorityItemJpaEntity {

    @Id
    @Column(name = "authority_id", length = 36, nullable = false)
    private String authorityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private OcrReviewJpaEntity review;

    @Column(name = "authority_type", length = 40, nullable = false)
    private String authorityType;

    @Column(name = "role_label", length = 200)
    private String roleLabel;

    @Column(name = "person_name", length = 300)
    private String personName;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "decision", length = 20, nullable = false)
    private String decision;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OcrAuthorityItemJpaEntity() {}

    public OcrAuthorityItemJpaEntity(String authorityId, String authorityType, String roleLabel,
                                     String personName, String content, Double confidence,
                                     String decision, Instant decidedAt, int sortOrder) {
        this.authorityId = authorityId;
        this.authorityType = authorityType;
        this.roleLabel = roleLabel;
        this.personName = personName;
        this.content = content;
        this.confidence = confidence;
        this.decision = decision;
        this.decidedAt = decidedAt;
        this.sortOrder = sortOrder;
    }

    void setReview(OcrReviewJpaEntity review) { this.review = review; }

    public String getAuthorityId()      { return authorityId; }
    public OcrReviewJpaEntity getReview() { return review; }
    public String getAuthorityType()    { return authorityType; }
    public String getRoleLabel()        { return roleLabel; }
    public String getPersonName()       { return personName; }
    public String getContent()          { return content; }
    public Double getConfidence()       { return confidence; }
    public String getDecision()         { return decision; }
    public Instant getDecidedAt()       { return decidedAt; }
    public int getSortOrder()           { return sortOrder; }
    public int getVersion()             { return version; }

    public void setDecision(String decision)      { this.decision = decision; }
    public void setDecidedAt(Instant decidedAt)   { this.decidedAt = decidedAt; }
}
