package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** JPA row for the {@code Bundle} composition aggregate. */
@Entity
@Table(name = "bundle")
public class BundleJpaEntity {

    @Id
    @Column(name = "bundle_id", length = 36, nullable = false)
    private String bundleId;

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "bundle_type", length = 50, nullable = false)
    private String bundleType;

    @Column(name = "expected_document_count", nullable = false)
    private int expectedDocumentCount;

    @Column(name = "assembly_status", length = 50, nullable = false)
    private String assemblyStatus;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bundle_document", joinColumns = @JoinColumn(name = "bundle_id"))
    private Set<BundleDocumentEmbeddable> documents = new LinkedHashSet<>();

    protected BundleJpaEntity() {}

    public BundleJpaEntity(String bundleId, String caseId, String tenantId, String bundleType,
                           int expectedDocumentCount, String assemblyStatus, Instant createdAt,
                           Set<BundleDocumentEmbeddable> documents) {
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.bundleType = bundleType;
        this.expectedDocumentCount = expectedDocumentCount;
        this.assemblyStatus = assemblyStatus;
        this.createdAt = createdAt;
        if (documents != null) this.documents = new LinkedHashSet<>(documents);
    }

    public String getBundleId() { return bundleId; }
    public String getCaseId() { return caseId; }
    public String getTenantId() { return tenantId; }
    public String getBundleType() { return bundleType; }
    public int getExpectedDocumentCount() { return expectedDocumentCount; }
    public String getAssemblyStatus() { return assemblyStatus; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<BundleDocumentEmbeddable> getDocuments() { return documents; }

    public void setAssemblyStatus(String assemblyStatus) { this.assemblyStatus = assemblyStatus; }
    public void setDocuments(Set<BundleDocumentEmbeddable> documents) {
        this.documents = documents == null ? new LinkedHashSet<>() : new LinkedHashSet<>(documents);
    }
}
