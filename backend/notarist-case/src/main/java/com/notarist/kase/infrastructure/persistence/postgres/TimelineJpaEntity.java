package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA row for the Timeline aggregate root. Owns its entries as a child collection; the collection is
 * append-only in practice (the aggregate never removes an entry) and the unique (timeline_id,
 * sequence) constraint on the child table rejects any racing duplicate.
 */
@Entity
@Table(name = "case_timeline")
public class TimelineJpaEntity {

    @Id
    @Column(name = "timeline_id", length = 36, nullable = false)
    private String timelineId;

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

    @OneToMany(mappedBy = "timeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequence ASC")
    private List<TimelineEntryJpaEntity> entries = new ArrayList<>();

    protected TimelineJpaEntity() {}

    public TimelineJpaEntity(String timelineId, String caseId, String tenantId, String status,
                             Instant createdAt) {
        this.timelineId = timelineId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addEntry(TimelineEntryJpaEntity entry) {
        entry.setTimeline(this);
        this.entries.add(entry);
    }

    public String getTimelineId() { return timelineId; }
    public String getCaseId() { return caseId; }
    public String getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public List<TimelineEntryJpaEntity> getEntries() { return entries; }

    public void setStatus(String status) { this.status = status; }
}
