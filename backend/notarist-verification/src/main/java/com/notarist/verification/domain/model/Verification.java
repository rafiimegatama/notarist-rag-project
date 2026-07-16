package com.notarist.verification.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.verification.domain.event.ChecklistCompleted;
import com.notarist.verification.domain.event.VerificationCompleted;
import com.notarist.verification.domain.event.VerificationFailed;
import com.notarist.verification.domain.event.VerificationReturned;
import com.notarist.verification.domain.exception.ChecklistItemNotFoundException;
import com.notarist.verification.domain.exception.IllegalVerificationTransitionException;
import com.notarist.verification.domain.exception.VerificationAuthorityException;
import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.state.VerificationStatus;
import com.notarist.verification.domain.state.VerificationStatusMachine;
import com.notarist.verification.domain.valueobject.BundleId;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Reviewer;
import com.notarist.verification.domain.valueobject.Role;
import com.notarist.verification.domain.valueobject.VerificationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root for a bundle's verification.
 *
 * <p><b>There is no public status setter.</b> Status changes only through {@link #changeStatus}, which
 * consults {@link VerificationStatusMachine} and rejects anything the table does not list — an illegal
 * transition is unreachable, not merely validated.
 *
 * <p><b>The completion rule is enforced here, not in a service.</b> A verification cannot become
 * VERIFIED unless every mandatory checklist item is PASS or NOT_APPLICABLE and no item is
 * MANUAL_REQUIRED. A FAIL item must carry a reason (enforced on the item itself).
 *
 * <p><b>Every checklist decision writes append-only history</b> and stamps the root (reviewer /
 * reviewedAt / opening PENDING → UNDER_VERIFICATION), which dirties it so the optimistic
 * {@code @Version} bumps — two verifiers on the same bundle concurrently cannot both win.
 */
public final class Verification extends AggregateRoot {

    /** Roles permitted to give the final VERIFIED sign-off. STAFF may decide items and fail, not verify. */
    private static final Set<Role> VERIFY_ROLES =
            EnumSet.of(Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN, Role.ADMIN);

    private final VerificationId verificationId;
    private final UUID bundleId;
    private final UUID tenantId;
    private final Instant createdAt;

    private VerificationStatus status;
    private UUID reviewerId;
    private Instant reviewedAt;

    private final List<ChecklistItem> items = new ArrayList<>();

    private final List<ItemAuditEntry> recordedAudits = new ArrayList<>();
    private int lastAuditSequence;

    private Verification(VerificationId verificationId, UUID bundleId, UUID tenantId,
                         VerificationStatus status, UUID reviewerId, Instant reviewedAt,
                         Instant createdAt, int lastAuditSequence) {
        this.verificationId = verificationId;
        this.bundleId = bundleId;
        this.tenantId = tenantId;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.lastAuditSequence = lastAuditSequence;
    }

    /** Provisions a fresh verification for a bundle. Starts PENDING. Raises no events. */
    public static Verification start(VerificationId verificationId, UUID bundleId, UUID tenantId,
                                     List<ChecklistItem> items) {
        Verification v = new Verification(verificationId, bundleId, tenantId,
                VerificationStatus.PENDING, null, null, Instant.now(), 0);
        if (items != null) v.items.addAll(items);
        v.validate();
        return v;
    }

    /** Rehydration from persistence. Bypasses events. */
    public static Verification rehydrate(VerificationId verificationId, UUID bundleId, UUID tenantId,
                                         VerificationStatus status, UUID reviewerId, Instant reviewedAt,
                                         Instant createdAt, List<ChecklistItem> items,
                                         int lastAuditSequence) {
        Verification v = new Verification(verificationId, bundleId, tenantId, status, reviewerId,
                reviewedAt, createdAt, lastAuditSequence);
        if (items != null) v.items.addAll(items);
        return v;
    }

    // ---- Operations ----------------------------------------------------------------------------

    /**
     * Records a decision on one checklist item, writes append-only history, and (if this settles the
     * last item) raises {@link ChecklistCompleted}. The first decision opens the verification
     * (PENDING → UNDER_VERIFICATION).
     */
    public void decideItem(ItemId itemId, Decision decision, String comment, Reviewer reviewer,
                           CorrelationId correlationId, TraceId traceId) {
        requireHuman(reviewer);
        if (status.isOutcome()) {
            throw new IllegalVerificationTransitionException(
                    "Verification " + verificationId + " is " + status
                            + " — return it to UNDER_VERIFICATION before changing checklist items");
        }

        ChecklistItem item = itemById(itemId);
        boolean wasFullyDecided = checklistFullyDecided();
        Decision previous = item.decision();

        item.decide(decision, comment, reviewer);

        if (status == VerificationStatus.PENDING) {
            this.status = VerificationStatus.UNDER_VERIFICATION;   // legal edge; first action opens it
        }
        stamp(reviewer);
        recordAudit(item, decision, previous, comment, reviewer);

        if (!wasFullyDecided && checklistFullyDecided()) {
            raise(new ChecklistCompleted(verificationId, BundleId.of(bundleId), tenantId,
                    reviewer.userId(), correlationId, traceId));
        }
        enforceInvariants();
    }

    /**
     * Moves the verification through its lifecycle. The single status-mutating operation.
     *
     * @throws IllegalVerificationTransitionException when the edge is not in the state machine
     * @throws VerificationInvariantViolationException when completing with a mandatory check unmet
     * @throws VerificationAuthorityException          when the actor's role may not verify
     */
    public void changeStatus(VerificationStatus target, Reviewer reviewer,
                             CorrelationId correlationId, TraceId traceId) {
        requireHuman(reviewer);

        if (!VerificationStatusMachine.isLegal(status, target)) {
            throw IllegalVerificationTransitionException.of(status, target);
        }

        VerificationStatus from = this.status;

        if (target == VerificationStatus.VERIFIED) {
            requireCompletable();
            if (!VERIFY_ROLES.contains(reviewer.role())) {
                throw new VerificationAuthorityException(
                        "Role " + reviewer.role() + " may not VERIFY (allowed: " + VERIFY_ROLES + ")");
            }
        }

        this.status = target;
        stamp(reviewer);
        enforceInvariants();

        if (VerificationStatusMachine.isReturn(from, target)) {
            raise(new VerificationReturned(verificationId, BundleId.of(bundleId), tenantId, from,
                    reviewer.userId(), correlationId, traceId));
            return;
        }
        switch (target) {
            case VERIFIED -> raise(new VerificationCompleted(verificationId, BundleId.of(bundleId),
                    tenantId, reviewer.userId(), correlationId, traceId));
            case FAILED -> raise(new VerificationFailed(verificationId, BundleId.of(bundleId),
                    tenantId, reviewer.userId(), correlationId, traceId));
            default -> { /* opening PENDING → UNDER_VERIFICATION carries no dedicated event */ }
        }
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (verificationId == null) throw new VerificationInvariantViolationException("verificationId is required");
        if (bundleId == null)       throw new VerificationInvariantViolationException("bundleId is required");
        if (tenantId == null)       throw new VerificationInvariantViolationException("tenantId is required");
        if (status == null)         throw new VerificationInvariantViolationException("status is required");
        if (status == VerificationStatus.VERIFIED && !isCompletable()) {
            throw new VerificationInvariantViolationException(
                    "Verification " + verificationId + " is VERIFIED but a mandatory check is unmet");
        }
    }

    /** The completion rule: every mandatory item PASS/NA, and nothing MANUAL_REQUIRED. */
    private boolean isCompletable() {
        boolean everyMandatoryAcceptable = items.stream()
                .filter(ChecklistItem::mandatory)
                .allMatch(i -> i.decision() != null && i.decision().isAcceptable());
        boolean noneBlocking = items.stream().noneMatch(ChecklistItem::isBlocking);
        return everyMandatoryAcceptable && noneBlocking;
    }

    private void requireCompletable() {
        long blocking = items.stream().filter(ChecklistItem::isBlocking).count();
        if (blocking > 0) {
            throw new VerificationInvariantViolationException(
                    "Cannot VERIFY — " + blocking + " item(s) still MANUAL_REQUIRED");
        }
        long unmetMandatory = items.stream()
                .filter(ChecklistItem::mandatory)
                .filter(i -> i.decision() == null || !i.decision().isAcceptable())
                .count();
        if (unmetMandatory > 0) {
            throw new VerificationInvariantViolationException(
                    "Cannot VERIFY — " + unmetMandatory
                            + " mandatory item(s) are not PASS or NOT_APPLICABLE");
        }
    }

    private void requireHuman(Reviewer reviewer) {
        if (reviewer == null || reviewer.isSystem()) {
            throw new VerificationAuthorityException(
                    "A verification action requires a human reviewer, not the system");
        }
    }

    private void stamp(Reviewer reviewer) {
        this.reviewerId = reviewer.userId();
        this.reviewedAt = Instant.now();
    }

    private void recordAudit(ChecklistItem item, Decision decision, Decision previous, String comment,
                             Reviewer reviewer) {
        this.lastAuditSequence += 1;
        recordedAudits.add(ItemAuditEntry.record(item.itemId(), decision, previous, comment,
                reviewer, lastAuditSequence));
    }

    private ChecklistItem itemById(ItemId itemId) {
        return items.stream()
                .filter(i -> i.itemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ChecklistItemNotFoundException(
                        "Item " + itemId + " is not part of verification " + verificationId));
    }

    private boolean checklistFullyDecided() {
        return !items.isEmpty() && items.stream().allMatch(ChecklistItem::isSettled);
    }

    // ---- Progress queries ----------------------------------------------------------------------

    public int totalItems()          { return items.size(); }
    public long passedCount()        { return countByDecision(Decision.PASS); }
    public long failedCount()        { return countByDecision(Decision.FAIL); }
    public long notApplicableCount() { return countByDecision(Decision.NOT_APPLICABLE); }
    public long manualRequiredCount(){ return countByDecision(Decision.MANUAL_REQUIRED); }
    public long remainingCount()     { return items.stream().filter(i -> !i.isSettled()).count(); }
    public boolean checklistComplete() { return checklistFullyDecided(); }
    public boolean completable()     { return isCompletable(); }

    private long countByDecision(Decision decision) {
        return items.stream().filter(i -> i.decision() == decision).count();
    }

    // ---- Accessors -----------------------------------------------------------------------------

    /** Audit entries recorded this unit of work; drained once by the repository after save. */
    public List<ItemAuditEntry> pullAuditEntries() {
        List<ItemAuditEntry> drained = List.copyOf(recordedAudits);
        recordedAudits.clear();
        return drained;
    }

    public VerificationId verificationId()  { return verificationId; }
    public UUID bundleId()                  { return bundleId; }
    public UUID tenantId()                  { return tenantId; }
    public VerificationStatus status()      { return status; }
    public UUID reviewerId()                { return reviewerId; }
    public Instant reviewedAt()             { return reviewedAt; }
    public Instant createdAt()              { return createdAt; }
    public int lastAuditSequence()          { return lastAuditSequence; }
    public List<ChecklistItem> items()      { return Collections.unmodifiableList(items); }
}
