package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.ApprovalGranted;
import com.notarist.kase.domain.event.ApprovalRejected;
import com.notarist.kase.domain.event.ApprovalRequested;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.state.ApprovalDecision;
import com.notarist.kase.domain.state.ApprovalStateMachine;
import com.notarist.kase.domain.valueobject.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a human decision that carries legal responsibility.
 *
 * <p>Assigned to a <b>role</b>; resolved by a <b>person</b>. Its own root (rather than an entity of
 * Case) because the primary query is cross-case — "what is waiting for my signature?" — and answering
 * that by loading every Case would be absurd.
 *
 * <p>Two rules are enforced here and nowhere else, because a controller check is bypassed the moment a
 * second caller reaches the same use case:
 *
 * <ul>
 *   <li><b>Notarial authority is statutory and personal.</b> A NOTARY_SIGNATURE may be decided only by
 *       a NOTARIS (or a PPAT_OFFICER for PPAT deeds). Not by ADMIN. <em>Not even by PIMPINAN.</em> An
 *       administrator who can sign deeds is a fraud vector, not a convenience.</li>
 *   <li><b>Four eyes.</b> The approver may not be the person whose work is being approved.</li>
 * </ul>
 */
public class Approval extends AggregateRoot {

    private final ApprovalId approvalId;
    private final CaseId caseId;
    private final ApprovalType approvalType;
    private final Role requiredRole;
    private final UUID tenantId;
    /** Whose work is under review — used to enforce four-eyes. Nullable when not applicable. */
    private final UUID submittedBy;
    private final Instant requestedAt;

    private ApprovalDecision decision;
    private Actor decidedBy;
    private String rejectionReason;
    private Instant decidedAt;

    private Approval(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType, Role requiredRole,
                     UUID tenantId, UUID submittedBy, ApprovalDecision decision, Instant requestedAt) {
        this.approvalId = approvalId;
        this.caseId = caseId;
        this.approvalType = approvalType;
        this.requiredRole = requiredRole;
        this.tenantId = tenantId;
        this.submittedBy = submittedBy;
        this.decision = decision;
        this.requestedAt = requestedAt;
    }

    public static Approval request(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType,
                                   Role requiredRole, UUID tenantId, UUID submittedBy,
                                   CorrelationId correlationId, TraceId traceId) {

        Approval a = new Approval(approvalId, caseId, approvalType, requiredRole, tenantId,
                submittedBy, ApprovalDecision.PENDING, Instant.now());
        a.validate();
        a.raise(new ApprovalRequested(approvalId, caseId, approvalType, requiredRole, tenantId,
                correlationId, traceId));
        return a;
    }

    public static Approval rehydrate(ApprovalId approvalId, CaseId caseId, ApprovalType approvalType,
                                     Role requiredRole, UUID tenantId, UUID submittedBy,
                                     ApprovalDecision decision, Actor decidedBy, String rejectionReason,
                                     Instant requestedAt, Instant decidedAt) {
        Approval a = new Approval(approvalId, caseId, approvalType, requiredRole, tenantId,
                submittedBy, decision, requestedAt);
        a.decidedBy = decidedBy;
        a.rejectionReason = rejectionReason;
        a.decidedAt = decidedAt;
        return a;
    }

    // ---- The only way the decision changes -----------------------------------------------------

    public void transition(ApprovalDecision target, Actor actor, String reason,
                           CorrelationId correlationId, TraceId traceId) {

        if (!ApprovalStateMachine.isLegal(decision, target)) {
            throw new IllegalTransitionException(
                    "Approval " + approvalId + ": " + decision + " → " + target + " is not permitted. "
                            + "A decision is never reversed — raise a new approval instead, because the "
                            + "original decision and its reversal are both legally significant facts.");
        }

        switch (target) {
            case APPROVED -> doApprove(actor, correlationId, traceId);
            case REJECTED -> doReject(actor, reason, correlationId, traceId);
            case EXPIRED  -> doExpire();
            default -> throw new IllegalTransitionException("Cannot transition to " + target);
        }
    }

    public void approve(Actor actor, CorrelationId correlationId, TraceId traceId) {
        transition(ApprovalDecision.APPROVED, actor, null, correlationId, traceId);
    }

    public void reject(Actor actor, String reason, CorrelationId correlationId, TraceId traceId) {
        transition(ApprovalDecision.REJECTED, actor, reason, correlationId, traceId);
    }

    private void doApprove(Actor actor, CorrelationId correlationId, TraceId traceId) {
        assertAuthority(actor);
        assertFourEyes(actor);

        this.decision = ApprovalDecision.APPROVED;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
        enforceInvariants();

        raise(new ApprovalGranted(approvalId, caseId, approvalType, actor, tenantId,
                correlationId, traceId));
    }

    private void doReject(Actor actor, String reason, CorrelationId correlationId, TraceId traceId) {
        assertAuthority(actor);
        assertFourEyes(actor);

        if (reason == null || reason.isBlank()) {
            throw new InvariantViolationException(
                    "A rejection requires a reason — the reason is a legally significant fact, "
                            + "not a courtesy");
        }

        this.decision = ApprovalDecision.REJECTED;
        this.decidedBy = actor;
        this.rejectionReason = reason;
        this.decidedAt = Instant.now();
        enforceInvariants();

        raise(new ApprovalRejected(approvalId, caseId, approvalType, actor, reason, tenantId,
                correlationId, traceId));
    }

    private void doExpire() {
        this.decision = ApprovalDecision.EXPIRED;
        this.decidedAt = Instant.now();
        enforceInvariants();
    }

    private void assertAuthority(Actor actor) {
        if (actor == null || actor.isSystem()) {
            throw new AuthorityException(
                    "An approval must be decided by a human. The system cannot assume legal responsibility.");
        }
        if (!approvalType.mayBeDecidedBy(actor.role())) {
            throw new AuthorityException(
                    "Role " + actor.role() + " may not decide a " + approvalType
                            + " approval (requires one of " + approvalType.allowedRoles() + "). "
                            + "Notarial authority is statutory and personal — it cannot be delegated upward.");
        }
    }

    private void assertFourEyes(Actor actor) {
        if (approvalType.requiresFourEyes()
                && submittedBy != null
                && submittedBy.equals(actor.userId())) {
            throw new AuthorityException(
                    "Four-eyes violation: " + actor.userId()
                            + " submitted this work and may not also approve it");
        }
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (approvalId == null)   throw new InvariantViolationException("approvalId is required");
        if (caseId == null)       throw new InvariantViolationException("caseId is required");
        if (approvalType == null) throw new InvariantViolationException("approvalType is required");
        if (requiredRole == null) throw new InvariantViolationException("requiredRole is required");
        if (tenantId == null)     throw new InvariantViolationException("tenantId is required");
        if (decision == null)     throw new InvariantViolationException("decision is required");

        if (decision == ApprovalDecision.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new InvariantViolationException("A REJECTED approval must carry a reason");
        }
        if (decision.isTerminal() && decision != ApprovalDecision.EXPIRED && decidedBy == null) {
            throw new InvariantViolationException("A decided approval must record who decided it");
        }
        if (decision.isTerminal() && decidedAt == null) {
            throw new InvariantViolationException("A decided approval must record when");
        }
    }

    // ---- Queries -------------------------------------------------------------------------------

    public boolean isPending()            { return decision == ApprovalDecision.PENDING; }
    public ApprovalId approvalId()        { return approvalId; }
    public CaseId caseId()                { return caseId; }
    public ApprovalType approvalType()    { return approvalType; }
    public Role requiredRole()            { return requiredRole; }
    public UUID tenantId()                { return tenantId; }
    public UUID submittedBy()             { return submittedBy; }
    public ApprovalDecision decision()    { return decision; }
    public Actor decidedBy()              { return decidedBy; }
    public String rejectionReason()       { return rejectionReason; }
    public Instant requestedAt()          { return requestedAt; }
    public Instant decidedAt()            { return decidedAt; }
}
