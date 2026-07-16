package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/** JPA row for the {@code BundleWorkflow} process aggregate (one per bundle). */
@Entity
@Table(name = "bundle_workflow")
public class BundleWorkflowJpaEntity {

    @Id
    @Column(name = "bundle_id", length = 36, nullable = false)
    private String bundleId;

    @Column(name = "case_id", length = 36, nullable = false)
    private String caseId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BundleWorkflowJpaEntity() {}

    public BundleWorkflowJpaEntity(String bundleId, String caseId, String tenantId, String status,
                                   Instant createdAt) {
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getBundleId() { return bundleId; }
    public String getCaseId() { return caseId; }
    public String getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
}
