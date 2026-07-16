package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.CaseCreated;
import com.notarist.kase.domain.event.CaseTransitioned;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.state.CaseStateMachine;
import com.notarist.kase.domain.state.TransitionKind;
import com.notarist.kase.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for a unit of notarial work.
 *
 * <p><b>There is no public state setter.</b> The only way {@code state} changes is
 * {@link #transition}, which consults {@link CaseStateMachine} and rejects anything the table does not
 * list. An illegal transition is therefore not "caught by validation" — it is unreachable.
 *
 * <p><b>Documents are not inside this aggregate.</b> A Case references Bundles by ID, and a Bundle
 * references Documents by ID. Five ingest workers mutate document state concurrently; pulling
 * documents in here would make every OCR completion contend on a business case, and would break the
 * thousands of documents that already exist with no case at all.
 *
 * <p><b>OCR_RUNNING is observed, never driven.</b> The Case learns that ingestion finished by
 * receiving an event; it never calls the pipeline, and the pipeline never knows a Case exists.
 */
public class Case extends AggregateRoot {

    private final CaseId caseId;
    private final CaseNumber caseNumber;
    private final CaseType caseType;
    private final UUID tenantId;
    private final UUID createdBy;
    private final Instant createdAt;

    private CaseState state;
    private UUID assignedNotarisId;
    private final List<BundleId> bundleIds = new ArrayList<>();
    private NomorAkta nomorAkta;          // allocated from the Repertorium at FINALIZED — never before
    private Instant closedAt;

    /**
     * Private. A Case can only be born through {@link com.notarist.kase.domain.factory.CaseFactory},
     * which guarantees it starts in CASE_CREATED. There is no other legal birth state, so no other
     * construction path may exist.
     */
    private Case(CaseId caseId, CaseNumber caseNumber, CaseType caseType, UUID tenantId,
                 UUID createdBy, UUID assignedNotarisId, CaseState state, Instant createdAt) {
        this.caseId = caseId;
        this.caseNumber = caseNumber;
        this.caseType = caseType;
        this.tenantId = tenantId;
        this.createdBy = createdBy;
        this.assignedNotarisId = assignedNotarisId;
        this.state = state;
        this.createdAt = createdAt;
    }

    /** Factory-only entry point. Raises {@link CaseCreated}. */
    public static Case open(CaseId caseId, CaseNumber caseNumber, CaseType caseType, UUID tenantId,
                            Actor actor, UUID assignedNotarisId,
                            CorrelationId correlationId, TraceId traceId) {

        if (actor == null || actor.isSystem()) {
            throw new AuthorityException("A Case must be opened by a human actor, not the system");
        }

        Case c = new Case(caseId, caseNumber, caseType, tenantId,
                actor.userId(), assignedNotarisId, CaseState.CASE_CREATED, Instant.now());
        c.validate();
        c.raise(new CaseCreated(caseId, caseNumber, caseType, tenantId, actor, correlationId, traceId));
        return c;
    }

    /** Rehydration from persistence. Bypasses events — loading is not a business act. */
    public static Case rehydrate(CaseId caseId, CaseNumber caseNumber, CaseType caseType, UUID tenantId,
                                 UUID createdBy, UUID assignedNotarisId, CaseState state,
                                 List<BundleId> bundleIds, NomorAkta nomorAkta,
                                 Instant createdAt, Instant closedAt) {
        Case c = new Case(caseId, caseNumber, caseType, tenantId, createdBy, assignedNotarisId,
                state, createdAt);
        if (bundleIds != null) c.bundleIds.addAll(bundleIds);
        c.nomorAkta = nomorAkta;
        c.closedAt = closedAt;
        return c;
    }

    // ---- The only way state changes ------------------------------------------------------------

    /**
     * Moves the case. The single mutating operation on this aggregate.
     *
     * @throws IllegalTransitionException when the edge is not in the state machine
     * @throws AuthorityException         when the actor's role may not take that edge
     * @throws InvariantViolationException when a rollback/cancel arrives without a reason
     */
    public void transition(CaseState target, Actor actor, TransitionReason reason,
                           CorrelationId correlationId, TraceId traceId) {

        if (actor == null) throw new AuthorityException("A transition requires an actor");

        if (state.isTerminal()) {
            throw new IllegalTransitionException(
                    "Case " + caseId + " is terminal (" + state + ") — no further transition is possible");
        }

        CaseStateMachine.Edge edge = CaseStateMachine.edge(state, target)
                .orElseThrow(() -> IllegalTransitionException.of(this, state, target));

        if (!edge.permits(actor.role())) {
            throw new AuthorityException(
                    "Role " + actor.role() + " may not transition " + state + " → " + target
                            + " (allowed: " + edge.allowedRoles() + ")");
        }

        // A rollback or cancellation without a stated reason is not auditable, and a notary must be
        // able to tell a regulator WHY a case went backwards. Refuse it.
        if (edge.kind().requiresReason() && (reason == null || !reason.isPresent())) {
            throw new InvariantViolationException(
                    "A " + edge.kind() + " transition (" + state + " → " + target + ") requires a reason");
        }

        CaseState from = this.state;
        this.state = target;
        if (target.isTerminal()) this.closedAt = Instant.now();

        enforceInvariants();

        raise(new CaseTransitioned(caseId, tenantId, from, target, edge.kind(),
                reason == null ? TransitionReason.NONE : reason, actor, correlationId, traceId));
    }

    /** Convenience for the common FORWARD case with no reason. */
    public void transition(CaseState target, Actor actor) {
        transition(target, actor, TransitionReason.NONE, null, null);
    }

    public void attachBundle(BundleId bundleId) {
        if (bundleId == null) throw new InvariantViolationException("bundleId must not be null");
        if (state.isTerminal()) {
            throw new InvariantViolationException("Cannot attach a bundle to a terminal case");
        }
        if (!bundleIds.contains(bundleId)) bundleIds.add(bundleId);   // idempotent: re-attach is a no-op
        enforceInvariants();
    }

    /**
     * Records the statutory akta number allocated by the Repertorium.
     *
     * <p>Deliberately narrow: only legal at FINALIZED, and only once. The number itself is produced by
     * the {@link Repertorium} aggregate, which owns the gapless sequence — a Case may not mint one.
     */
    public void assignNomorAkta(NomorAkta allocated) {
        if (state != CaseState.FINALIZED) {
            throw new InvariantViolationException(
                    "A nomor akta may only be assigned to a FINALIZED case (was " + state + ")");
        }
        if (this.nomorAkta != null) {
            throw new InvariantViolationException(
                    "Case " + caseId + " already carries nomor akta " + this.nomorAkta
                            + " — a repertorium number is allocated exactly once, never reissued");
        }
        this.nomorAkta = allocated;
        enforceInvariants();
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (caseId == null)     throw new InvariantViolationException("caseId is required");
        if (caseNumber == null) throw new InvariantViolationException("caseNumber is required");
        if (caseType == null)   throw new InvariantViolationException("caseType is required");
        if (tenantId == null)   throw new InvariantViolationException("tenantId is required");
        if (state == null)      throw new InvariantViolationException("state is required");

        // A signed deed must carry its statutory number, and an unsigned one must not.
        if (state == CaseState.DELIVERED || state == CaseState.ARCHIVED) {
            if (nomorAkta == null) {
                throw new InvariantViolationException(
                        "Case " + caseId + " is " + state + " but carries no nomor akta — "
                                + "a delivered deed without a repertorium entry is a regulatory defect");
            }
        }
        if (nomorAkta != null && !isAtOrBeyondFinalized()) {
            throw new InvariantViolationException(
                    "Case " + caseId + " carries a nomor akta in state " + state
                            + " — a number may only exist from FINALIZED onwards");
        }
        if (state.isTerminal() && closedAt == null) {
            throw new InvariantViolationException("A terminal case must record closedAt");
        }
    }

    private boolean isAtOrBeyondFinalized() {
        return state == CaseState.FINALIZED
                || state == CaseState.DELIVERED
                || state == CaseState.ARCHIVED;
    }

    // ---- Queries -------------------------------------------------------------------------------

    public boolean canTransitionTo(CaseState target) {
        return !state.isTerminal() && CaseStateMachine.isLegal(state, target);
    }

    public CaseId caseId()               { return caseId; }
    public CaseNumber caseNumber()       { return caseNumber; }
    public CaseType caseType()           { return caseType; }
    public UUID tenantId()               { return tenantId; }
    public UUID createdBy()              { return createdBy; }
    public UUID assignedNotarisId()      { return assignedNotarisId; }
    public CaseState state()             { return state; }
    public NomorAkta nomorAkta()         { return nomorAkta; }
    public Instant createdAt()           { return createdAt; }
    public Instant closedAt()            { return closedAt; }
    public boolean isTerminal()          { return state.isTerminal(); }
    public List<BundleId> bundleIds()    { return Collections.unmodifiableList(bundleIds); }
}
