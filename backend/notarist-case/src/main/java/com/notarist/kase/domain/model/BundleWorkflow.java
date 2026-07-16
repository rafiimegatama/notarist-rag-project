package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.BundleWorkflowTransitioned;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.BundleWorkflowStateMachine;
import com.notarist.kase.domain.state.BundleWorkflowStatus;
import com.notarist.kase.domain.valueobject.Actor;
import com.notarist.kase.domain.valueobject.BundleId;
import com.notarist.kase.domain.valueobject.CaseId;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a bundle's PROCESS lifecycle (verification → QC → delivery). Coordinated with,
 * but separate from, the {@link Bundle} composition aggregate that shares its {@link BundleId}.
 *
 * <p>As with {@link Case}, there is no public state setter: {@link #transition} is the only door, and
 * it consults {@link BundleWorkflowStateMachine}, so an illegal move is unreachable rather than
 * merely rejected. The cross-aggregate delivery precondition ("case must be approved") is NOT enforced
 * here — the aggregate cannot see the Case — it is enforced by the application transition service
 * before it asks for the DELIVERED move.
 */
public class BundleWorkflow extends AggregateRoot {

    private final BundleId bundleId;
    private final CaseId caseId;
    private final UUID tenantId;
    private final Instant createdAt;

    private BundleWorkflowStatus status;

    private BundleWorkflow(BundleId bundleId, CaseId caseId, UUID tenantId,
                           BundleWorkflowStatus status, Instant createdAt) {
        this.bundleId = bundleId;
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** A new workflow starts at OPEN. */
    public static BundleWorkflow start(BundleId bundleId, CaseId caseId, UUID tenantId) {
        BundleWorkflow w = new BundleWorkflow(bundleId, caseId, tenantId,
                BundleWorkflowStatus.OPEN, Instant.now());
        w.validate();
        return w;
    }

    public static BundleWorkflow rehydrate(BundleId bundleId, CaseId caseId, UUID tenantId,
                                           BundleWorkflowStatus status, Instant createdAt) {
        return new BundleWorkflow(bundleId, caseId, tenantId, status, createdAt);
    }

    /** The only way status changes. */
    public void transition(BundleWorkflowStatus target, Actor actor,
                           CorrelationId correlationId, TraceId traceId) {
        if (actor == null) throw new AuthorityException("A bundle transition requires an actor");
        if (actor.isSystem()) {
            throw new AuthorityException("The bundle workflow is human-driven; SYSTEM may not move it");
        }
        if (status.isTerminal()) {
            throw new IllegalTransitionException(
                    "Bundle " + bundleId + " is " + status + " — no further transition is possible");
        }
        if (!BundleWorkflowStateMachine.isLegal(status, target)) {
            throw IllegalTransitionException.of(this, status, target);
        }

        BundleWorkflowStatus from = this.status;
        this.status = target;
        enforceInvariants();

        raise(new BundleWorkflowTransitioned(bundleId, caseId, from, target, tenantId, actor,
                correlationId, traceId));
    }

    @Override
    public void validate() {
        if (bundleId == null) throw new InvariantViolationException("bundleId is required");
        if (caseId == null)   throw new InvariantViolationException("caseId is required");
        if (tenantId == null) throw new InvariantViolationException("tenantId is required");
        if (status == null)   throw new InvariantViolationException("status is required");
    }

    public boolean canTransitionTo(BundleWorkflowStatus target) {
        return !status.isTerminal() && BundleWorkflowStateMachine.isLegal(status, target);
    }

    public BundleId bundleId()          { return bundleId; }
    public CaseId caseId()              { return caseId; }
    public UUID tenantId()              { return tenantId; }
    public BundleWorkflowStatus status(){ return status; }
    public Instant createdAt()          { return createdAt; }
    public boolean isTerminal()         { return status.isTerminal(); }
}
