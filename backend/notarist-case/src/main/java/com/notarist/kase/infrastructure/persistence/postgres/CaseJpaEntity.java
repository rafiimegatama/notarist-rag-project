package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JPA row for the Case aggregate.
 *
 * <p>The state, nomorAkta, closedAt and bundleIds are the only mutable columns — everything else is
 * fixed at birth. Mutation flows through {@link CaseRepositoryImpl#save}, which loads the managed
 * entity and copies the new values in, so Hibernate's dirty checking and the {@link Version} column
 * do the optimistic-lock work rather than a blind overwrite.
 */
@Entity
@Table(name = "notarial_case")
public class CaseJpaEntity {

    @Id
    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "case_number", length = 100, nullable = false)
    private String caseNumber;

    @Column(name = "case_type", length = 50, nullable = false)
    private String caseType;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "created_by", length = 36, nullable = false)
    private String createdBy;

    @Column(name = "assigned_notaris_id", length = 36)
    private String assignedNotarisId;

    @Column(name = "state", length = 50, nullable = false)
    private String state;

    @Column(name = "nomor_akta", length = 100)
    private String nomorAkta;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "case_bundle_ref",
            joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "bundle_id", length = 36, nullable = false)
    private Set<String> bundleIds = new LinkedHashSet<>();

    protected CaseJpaEntity() {}

    public CaseJpaEntity(String caseId, String caseNumber, String caseType, String tenantId,
                         String createdBy, String assignedNotarisId, String state, String nomorAkta,
                         Instant createdAt, Instant closedAt, Set<String> bundleIds) {
        this.caseId = caseId;
        this.caseNumber = caseNumber;
        this.caseType = caseType;
        this.tenantId = tenantId;
        this.createdBy = createdBy;
        this.assignedNotarisId = assignedNotarisId;
        this.state = state;
        this.nomorAkta = nomorAkta;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        if (bundleIds != null) this.bundleIds = new LinkedHashSet<>(bundleIds);
    }

    public String getCaseId() { return caseId; }
    public String getCaseNumber() { return caseNumber; }
    public String getCaseType() { return caseType; }
    public String getTenantId() { return tenantId; }
    public String getCreatedBy() { return createdBy; }
    public String getAssignedNotarisId() { return assignedNotarisId; }
    public String getState() { return state; }
    public String getNomorAkta() { return nomorAkta; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
    public Set<String> getBundleIds() { return bundleIds; }

    // Only the fields a legal transition may change carry setters. There is no setter for caseId,
    // caseNumber, caseType, tenantId, createdBy or createdAt — those never change after creation.
    public void setState(String state) { this.state = state; }
    public void setNomorAkta(String nomorAkta) { this.nomorAkta = nomorAkta; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public void setAssignedNotarisId(String assignedNotarisId) { this.assignedNotarisId = assignedNotarisId; }
    public void setBundleIds(Set<String> bundleIds) {
        this.bundleIds = bundleIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(bundleIds);
    }
}
