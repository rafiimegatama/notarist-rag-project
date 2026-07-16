package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA row for the bundle's append-only timeline. */
@Entity
@Table(name = "bundle_timeline")
public class BundleTimelineJpaEntity {

    @Id
    @Column(name = "timeline_id", length = 36, nullable = false)
    private String timelineId;

    @Column(name = "bundle_id", length = 36, nullable = false)
    private String bundleId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "timeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequence ASC")
    private List<BundleTimelineEntryJpaEntity> entries = new ArrayList<>();

    protected BundleTimelineJpaEntity() {}

    public BundleTimelineJpaEntity(String timelineId, String bundleId, String tenantId, String status,
                                   Instant createdAt) {
        this.timelineId = timelineId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addEntry(BundleTimelineEntryJpaEntity entry) {
        entry.setTimeline(this);
        this.entries.add(entry);
    }

    public String getTimelineId() { return timelineId; }
    public String getBundleId() { return bundleId; }
    public String getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public List<BundleTimelineEntryJpaEntity> getEntries() { return entries; }

    public void setStatus(String status) { this.status = status; }
}
