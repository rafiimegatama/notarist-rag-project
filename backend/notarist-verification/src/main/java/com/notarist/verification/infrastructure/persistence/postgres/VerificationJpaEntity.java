package com.notarist.verification.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA row for the {@link com.notarist.verification.domain.model.Verification} aggregate root.
 *
 * <p>Only the verification-progress columns are mutable (status, reviewer_id, reviewed_at,
 * last_audit_sequence) plus the checklist children. Mutation flows through
 * {@code VerificationRepositoryImpl.save}, which loads the managed entity and copies the new values
 * in, so Hibernate's dirty checking and the {@link Version} column enforce optimistic locking. Every
 * checklist decision stamps reviewer_id/reviewed_at here, so the root is dirtied and its version bumps
 * — that guards two verifiers editing the same bundle concurrently.
 */
@Entity
@Table(name = "verification")
public class VerificationJpaEntity {

    @Id
    @Column(name = "verification_id", length = 36, nullable = false)
    private String verificationId;

    @Column(name = "bundle_id", length = 36, nullable = false)
    private String bundleId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "status", length = 40, nullable = false)
    private String status;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "last_audit_sequence", nullable = false)
    private int lastAuditSequence;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VerificationChecklistItemJpaEntity> items = new ArrayList<>();

    protected VerificationJpaEntity() {}

    public VerificationJpaEntity(String verificationId, String bundleId, String tenantId, String status,
                                 String reviewerId, Instant reviewedAt, int lastAuditSequence,
                                 Instant createdAt) {
        this.verificationId = verificationId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.lastAuditSequence = lastAuditSequence;
        this.createdAt = createdAt;
    }

    public void addItem(VerificationChecklistItemJpaEntity item) {
        item.setVerification(this);
        this.items.add(item);
    }

    public String getVerificationId()   { return verificationId; }
    public String getBundleId()         { return bundleId; }
    public String getTenantId()         { return tenantId; }
    public String getStatus()           { return status; }
    public String getReviewerId()       { return reviewerId; }
    public Instant getReviewedAt()      { return reviewedAt; }
    public int getLastAuditSequence()   { return lastAuditSequence; }
    public int getVersion()             { return version; }
    public Instant getCreatedAt()       { return createdAt; }
    public List<VerificationChecklistItemJpaEntity> getItems() { return items; }

    // Only the progress columns carry setters.
    public void setStatus(String status)                    { this.status = status; }
    public void setReviewerId(String reviewerId)            { this.reviewerId = reviewerId; }
    public void setReviewedAt(Instant reviewedAt)           { this.reviewedAt = reviewedAt; }
    public void setLastAuditSequence(int lastAuditSequence) { this.lastAuditSequence = lastAuditSequence; }
}
