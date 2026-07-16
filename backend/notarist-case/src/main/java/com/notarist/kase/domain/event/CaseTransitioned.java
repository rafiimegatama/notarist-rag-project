package com.notarist.kase.domain.event;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.state.TransitionKind;
import com.notarist.kase.domain.valueobject.*;

import java.util.UUID;

/**
 * The Case moved. The single most consumed event in the system: Timeline appends an entry, Reminder
 * schedules/cancels, Audit records it.
 *
 * <p>Carries the transition KIND, so "how often are drafts rolled back, and why?" is a filter rather
 * than an archaeological reconstruction from state diffs.
 */
public final class CaseTransitioned extends CaseDomainEvent {

    private final CaseId caseId;
    private final UUID tenantId;
    private final CaseState fromState;
    private final CaseState toState;
    private final TransitionKind kind;
    private final TransitionReason reason;
    private final Actor actor;

    public CaseTransitioned(CaseId caseId, UUID tenantId, CaseState fromState, CaseState toState,
                            TransitionKind kind, TransitionReason reason, Actor actor,
                            CorrelationId correlationId, TraceId traceId) {
        super(correlationId, traceId);
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.fromState = fromState;
        this.toState = toState;
        this.kind = kind;
        this.reason = reason;
        this.actor = actor;
    }

    @Override public String eventType() { return "CaseTransitioned"; }

    public CaseId caseId()            { return caseId; }
    public UUID tenantId()            { return tenantId; }
    public CaseState fromState()      { return fromState; }
    public CaseState toState()        { return toState; }
    public TransitionKind kind()      { return kind; }
    public TransitionReason reason()  { return reason; }
    public Actor actor()              { return actor; }
}
