package com.notarist.kase.domain.model;

import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.TimelineStateMachine;
import com.notarist.kase.domain.state.TimelineStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.TimelineEntryId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Append-only story of a bundle. The bundle counterpart of {@link Timeline} — it reuses the same
 * {@link TimelineEntry}, {@link com.notarist.kase.domain.model.TimelineEntryType},
 * {@link TimelineStatus} and {@link TimelineStateMachine}, keyed by {@link BundleId} instead of a
 * case. Entries are never modified or removed; the dense-sequence invariant makes a removal detectable.
 *
 * <p>Unlike the case {@link Timeline}, appends here raise no domain event — the bundle's own
 * {@code BundleWorkflowTransitioned} already carries the state change; the timeline is the persisted
 * record the UI reads.
 */
public class BundleTimeline extends AggregateRoot {

    private final UUID timelineId;
    private final BundleId bundleId;
    private final UUID tenantId;
    private final Instant createdAt;

    private TimelineStatus status;
    private final List<TimelineEntry> entries = new ArrayList<>();

    private BundleTimeline(UUID timelineId, BundleId bundleId, UUID tenantId,
                           TimelineStatus status, Instant createdAt) {
        this.timelineId = timelineId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static BundleTimeline start(UUID timelineId, BundleId bundleId, UUID tenantId) {
        BundleTimeline t = new BundleTimeline(timelineId, bundleId, tenantId,
                TimelineStatus.ACTIVE, Instant.now());
        t.validate();
        return t;
    }

    public static BundleTimeline rehydrate(UUID timelineId, BundleId bundleId, UUID tenantId,
                                           TimelineStatus status, List<TimelineEntry> entries,
                                           Instant createdAt) {
        BundleTimeline t = new BundleTimeline(timelineId, bundleId, tenantId, status, createdAt);
        if (entries != null) t.entries.addAll(entries);
        return t;
    }

    public TimelineEntryId append(TimelineEntryType type, String description, Actor actor) {
        if (!status.acceptsEntries()) {
            throw new IllegalTransitionException(
                    "Bundle timeline " + timelineId + " is SEALED — its record is final");
        }
        if (type == null) throw new InvariantViolationException("entry type is required");
        if (description == null || description.isBlank()) {
            throw new InvariantViolationException("A timeline entry must describe what happened");
        }
        TimelineEntry entry = new TimelineEntry(
                TimelineEntryId.generate(), type, description, actor, Instant.now(), entries.size());
        entries.add(entry);
        enforceInvariants();
        return entry.entryId();
    }

    public void seal() {
        if (!TimelineStateMachine.isLegal(status, TimelineStatus.SEALED)) {
            throw IllegalTransitionException.of(this, status, TimelineStatus.SEALED);
        }
        this.status = TimelineStatus.SEALED;
        enforceInvariants();
    }

    @Override
    public void validate() {
        if (timelineId == null) throw new InvariantViolationException("timelineId is required");
        if (bundleId == null)   throw new InvariantViolationException("bundleId is required");
        if (tenantId == null)   throw new InvariantViolationException("tenantId is required");
        if (status == null)     throw new InvariantViolationException("status is required");
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sequence() != i) {
                throw new InvariantViolationException(
                        "Bundle timeline " + timelineId + " has a sequence gap at position " + i);
            }
        }
    }

    public UUID timelineId()             { return timelineId; }
    public BundleId bundleId()           { return bundleId; }
    public UUID tenantId()               { return tenantId; }
    public TimelineStatus status()       { return status; }
    public int entryCount()              { return entries.size(); }
    public Instant createdAt()           { return createdAt; }
    public List<TimelineEntry> entries() { return Collections.unmodifiableList(entries); }
}
