package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.TimelineEntryAppended;
import com.notarist.kase.domain.event.TimelineSealed;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.TimelineStateMachine;
import com.notarist.kase.domain.state.TimelineStatus;
import com.notarist.kase.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the story of a case: what happened, when, and who did it.
 *
 * <p><b>Append-only.</b> There is no {@code update}, no {@code delete}, no {@code correct}. An entry
 * that was wrong is followed by another entry saying so — the original stays. That is the difference
 * between a record and a story someone can rewrite afterwards, and it is the only version a regulator
 * will accept.
 *
 * <p><b>Why this is an aggregate and not just a projection over {@code audit_trail}.</b> The earlier
 * design proposed deriving the timeline from the audit table to avoid duplicating data, and that
 * remains the right call for the *read model* the UI renders. This aggregate is a different thing: it
 * is the domain's own append-only record, which the domain controls and seals. Audit is a
 * compliance-wide store that may be rotated or archived on its own schedule; the case's story must not
 * depend on that. The two are reconciled in the docs: Timeline is the domain record, the UI timeline
 * is still a projection.
 */
public class Timeline extends AggregateRoot {

    private final TimelineId timelineId;
    private final CaseId caseId;
    private final UUID tenantId;
    private final Instant createdAt;

    private TimelineStatus status;
    private final List<TimelineEntry> entries = new ArrayList<>();

    private Timeline(TimelineId timelineId, CaseId caseId, UUID tenantId,
                     TimelineStatus status, Instant createdAt) {
        this.timelineId = timelineId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Timeline start(TimelineId timelineId, CaseId caseId, UUID tenantId) {
        Timeline t = new Timeline(timelineId, caseId, tenantId, TimelineStatus.ACTIVE, Instant.now());
        t.validate();
        return t;
    }

    public static Timeline rehydrate(TimelineId timelineId, CaseId caseId, UUID tenantId,
                                     TimelineStatus status, List<TimelineEntry> entries,
                                     Instant createdAt) {
        Timeline t = new Timeline(timelineId, caseId, tenantId, status, createdAt);
        if (entries != null) t.entries.addAll(entries);
        return t;
    }

    // ---- Append --------------------------------------------------------------------------------

    /**
     * Appends an entry. The only mutating operation, other than sealing.
     *
     * @throws IllegalTransitionException when the timeline is sealed
     */
    public TimelineEntryId append(TimelineEntryType type, String description, Actor actor,
                                  CorrelationId correlationId, TraceId traceId) {

        if (!status.acceptsEntries()) {
            throw new IllegalTransitionException(
                    "Timeline " + timelineId + " is SEALED — the case is closed and its record is final");
        }
        if (type == null) throw new InvariantViolationException("entry type is required");
        if (description == null || description.isBlank()) {
            throw new InvariantViolationException("A timeline entry must describe what happened");
        }

        TimelineEntry entry = new TimelineEntry(
                TimelineEntryId.generate(), type, description, actor, Instant.now(), entries.size());
        entries.add(entry);
        enforceInvariants();

        raise(new TimelineEntryAppended(timelineId, entry.entryId(), caseId,
                type.name(), description, tenantId, correlationId, traceId));

        return entry.entryId();
    }

    /** Seals the timeline. Called when the case reaches a terminal state. Irreversible. */
    public void transition(TimelineStatus target, CorrelationId correlationId, TraceId traceId) {
        if (!TimelineStateMachine.isLegal(status, target)) {
            throw IllegalTransitionException.of(this, status, target);
        }
        this.status = target;
        enforceInvariants();

        if (target == TimelineStatus.SEALED) {
            raise(new TimelineSealed(timelineId, caseId, entries.size(), tenantId,
                    correlationId, traceId));
        }
    }

    public void seal(CorrelationId correlationId, TraceId traceId) {
        transition(TimelineStatus.SEALED, correlationId, traceId);
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (timelineId == null) throw new InvariantViolationException("timelineId is required");
        if (caseId == null)     throw new InvariantViolationException("caseId is required");
        if (tenantId == null)   throw new InvariantViolationException("tenantId is required");
        if (status == null)     throw new InvariantViolationException("status is required");

        // Sequence numbers are dense and ordered — a gap would mean an entry was removed, which is
        // exactly the thing this aggregate exists to make impossible.
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sequence() != i) {
                throw new InvariantViolationException(
                        "Timeline " + timelineId + " has a sequence gap at position " + i
                                + " — entries are append-only and may never be removed");
            }
        }
    }

    // ---- Queries -------------------------------------------------------------------------------

    public TimelineId timelineId()       { return timelineId; }
    public CaseId caseId()               { return caseId; }
    public UUID tenantId()               { return tenantId; }
    public TimelineStatus status()       { return status; }
    public int entryCount()              { return entries.size(); }
    public Instant createdAt()           { return createdAt; }
    public List<TimelineEntry> entries() { return Collections.unmodifiableList(entries); }
}
