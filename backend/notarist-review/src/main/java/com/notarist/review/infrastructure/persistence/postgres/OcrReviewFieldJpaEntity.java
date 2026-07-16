package com.notarist.review.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA row for one reviewed field — a child of {@link OcrReviewJpaEntity}. The extracted value and the
 * bounding box are fixed at birth; only the reviewer's decision (decision, corrected_value,
 * rejection_reason, reviewer_id, reviewed_at) is mutable.
 */
@Entity
@Table(name = "ocr_review_field")
public class OcrReviewFieldJpaEntity {

    @Id
    @Column(name = "field_id", length = 36, nullable = false)
    private String fieldId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private OcrReviewJpaEntity review;

    @Column(name = "field_name", length = 200, nullable = false)
    private String fieldName;

    @Column(name = "display_label", length = 200, nullable = false)
    private String displayLabel;

    @Column(name = "extracted_value", length = 4000)
    private String extractedValue;

    @Column(name = "corrected_value", length = 4000)
    private String correctedValue;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "confidence_level", length = 20, nullable = false)
    private String confidenceLevel;

    @Column(name = "decision", length = 30, nullable = false)
    private String decision;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "bbox_page", nullable = false)
    private int bboxPage;

    @Column(name = "bbox_x", nullable = false)
    private double bboxX;

    @Column(name = "bbox_y", nullable = false)
    private double bboxY;

    @Column(name = "bbox_width", nullable = false)
    private double bboxWidth;

    @Column(name = "bbox_height", nullable = false)
    private double bboxHeight;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OcrReviewFieldJpaEntity() {}

    public OcrReviewFieldJpaEntity(String fieldId, String fieldName, String displayLabel,
                                   String extractedValue, String correctedValue, double confidence,
                                   String confidenceLevel, String decision, String rejectionReason,
                                   String reviewerId, Instant reviewedAt, int bboxPage, double bboxX,
                                   double bboxY, double bboxWidth, double bboxHeight, int sortOrder) {
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.displayLabel = displayLabel;
        this.extractedValue = extractedValue;
        this.correctedValue = correctedValue;
        this.confidence = confidence;
        this.confidenceLevel = confidenceLevel;
        this.decision = decision;
        this.rejectionReason = rejectionReason;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.bboxPage = bboxPage;
        this.bboxX = bboxX;
        this.bboxY = bboxY;
        this.bboxWidth = bboxWidth;
        this.bboxHeight = bboxHeight;
        this.sortOrder = sortOrder;
    }

    void setReview(OcrReviewJpaEntity review) { this.review = review; }

    public String getFieldId()          { return fieldId; }
    public OcrReviewJpaEntity getReview() { return review; }
    public String getFieldName()        { return fieldName; }
    public String getDisplayLabel()     { return displayLabel; }
    public String getExtractedValue()   { return extractedValue; }
    public String getCorrectedValue()   { return correctedValue; }
    public double getConfidence()       { return confidence; }
    public String getConfidenceLevel()  { return confidenceLevel; }
    public String getDecision()         { return decision; }
    public String getRejectionReason()  { return rejectionReason; }
    public String getReviewerId()       { return reviewerId; }
    public Instant getReviewedAt()      { return reviewedAt; }
    public int getBboxPage()            { return bboxPage; }
    public double getBboxX()            { return bboxX; }
    public double getBboxY()            { return bboxY; }
    public double getBboxWidth()        { return bboxWidth; }
    public double getBboxHeight()       { return bboxHeight; }
    public int getSortOrder()           { return sortOrder; }
    public int getVersion()             { return version; }

    // Mutable review-decision columns only.
    public void setCorrectedValue(String correctedValue) { this.correctedValue = correctedValue; }
    public void setDecision(String decision)             { this.decision = decision; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setReviewerId(String reviewerId)         { this.reviewerId = reviewerId; }
    public void setReviewedAt(Instant reviewedAt)        { this.reviewedAt = reviewedAt; }
}
