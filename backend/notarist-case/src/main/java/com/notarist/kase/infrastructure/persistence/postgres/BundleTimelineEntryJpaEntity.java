package com.notarist.kase.infrastructure.persistence.postgres;

import jakarta.persistence.*;

import java.time.Instant;

/** JPA row for one immutable bundle timeline entry. */
@Entity
@Table(name = "bundle_timeline_entry")
public class BundleTimelineEntryJpaEntity {

    @Id
    @Column(name = "entry_id", length = 36, nullable = false)
    private String entryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timeline_id", nullable = false)
    private BundleTimelineJpaEntity timeline;

    @Column(name = "entry_type", length = 50, nullable = false)
    private String entryType;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "actor_user_id", length = 36, nullable = false)
    private String actorUserId;

    @Column(name = "actor_role", length = 50, nullable = false)
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    protected BundleTimelineEntryJpaEntity() {}

    public BundleTimelineEntryJpaEntity(String entryId, String entryType, String description,
                                        String actorUserId, String actorRole, Instant occurredAt,
                                        int sequence) {
        this.entryId = entryId;
        this.entryType = entryType;
        this.description = description;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    public String getEntryId() { return entryId; }
    public String getEntryType() { return entryType; }
    public String getDescription() { return description; }
    public String getActorUserId() { return actorUserId; }
    public String getActorRole() { return actorRole; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getSequence() { return sequence; }

    void setTimeline(BundleTimelineJpaEntity timeline) { this.timeline = timeline; }
}
