package com.notarist.review.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.review.domain.event.FieldReviewed;
import com.notarist.review.domain.event.ReviewCompleted;
import com.notarist.review.domain.event.ReviewRejected;
import com.notarist.review.domain.event.ReviewVerified;
import com.notarist.review.domain.exception.FieldNotFoundException;
import com.notarist.review.domain.exception.IllegalReviewTransitionException;
import com.notarist.review.domain.exception.ReviewAuthorityException;
import com.notarist.review.domain.exception.ReviewInvariantViolationException;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.state.ReviewStatus;
import com.notarist.review.domain.state.ReviewStatusMachine;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.Reviewer;
import com.notarist.review.domain.valueobject.ReviewId;
import com.notarist.review.domain.valueobject.Role;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root for a document's OCR review.
 *
 * <p><b>There is no public status setter.</b> Status changes only through {@link #changeStatus}, which
 * consults {@link ReviewStatusMachine} and rejects anything the table does not list — an illegal
 * transition is unreachable, not merely validated. The linear lifecycle is
 * PENDING → IN_PROGRESS → REVIEW_COMPLETED → VERIFIED.
 *
 * <p><b>Every field decision writes append-only history.</b> {@link #reviewField} records a
 * {@link FieldAuditEntry} for the decision; history is never overwritten. The extracted OCR value is
 * immutable — corrections live alongside it — so the original is always recoverable.
 *
 * <p><b>The review root is stamped on every field decision.</b> reviewer/reviewedAt (and, on the first
 * action, PENDING → IN_PROGRESS) change with each decision, which dirties the root so its optimistic
 * {@code @Version} bumps — two reviewers acting on the same document concurrently cannot both win.
 */
public final class OcrReview extends AggregateRoot {

    /** Roles permitted to give the final VERIFIED sign-off — the liability boundary. STAFF may not. */
    private static final Set<Role> VERIFY_ROLES =
            EnumSet.of(Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN, Role.ADMIN);

    private final ReviewId reviewId;
    private final UUID documentId;
    private final UUID tenantId;
    private final String documentName;
    private final int pageCount;
    private final boolean stampDetected;
    private final boolean signatureDetected;
    private final double overallConfidence;
    private final Instant createdAt;

    private ReviewStatus status;
    private UUID reviewerId;
    private Instant reviewedAt;

    private final List<FieldReview> fields = new ArrayList<>();
    private final List<AuthorityItem> authorityItems = new ArrayList<>();

    // Audit entries recorded during this unit of work, drained once by the repository after save.
    private final List<FieldAuditEntry> recordedAudits = new ArrayList<>();
    private int lastAuditSequence;

    private OcrReview(ReviewId reviewId, UUID documentId, UUID tenantId, String documentName,
                      int pageCount, boolean stampDetected, boolean signatureDetected,
                      double overallConfidence, ReviewStatus status, UUID reviewerId,
                      Instant reviewedAt, Instant createdAt, int lastAuditSequence) {
        this.reviewId = reviewId;
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.documentName = documentName;
        this.pageCount = pageCount;
        this.stampDetected = stampDetected;
        this.signatureDetected = signatureDetected;
        this.overallConfidence = overallConfidence;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.lastAuditSequence = lastAuditSequence;
    }

    /**
     * Provisions a fresh review from completed OCR extraction. All fields start NEEDS_REVIEW and the
     * review starts PENDING. Raises no events — provisioning is not a review act.
     */
    public static OcrReview start(ReviewId reviewId, UUID documentId, UUID tenantId, String documentName,
                                  int pageCount, boolean stampDetected, boolean signatureDetected,
                                  double overallConfidence, List<FieldReview> fields,
                                  List<AuthorityItem> authorityItems) {
        OcrReview r = new OcrReview(reviewId, documentId, tenantId, documentName, pageCount,
                stampDetected, signatureDetected, overallConfidence, ReviewStatus.PENDING,
                null, null, Instant.now(), 0);
        if (fields != null) r.fields.addAll(fields);
        if (authorityItems != null) r.authorityItems.addAll(authorityItems);
        r.validate();
        return r;
    }

    /** Rehydration from persistence. Bypasses events — loading is not a business act. */
    public static OcrReview rehydrate(ReviewId reviewId, UUID documentId, UUID tenantId,
                                      String documentName, int pageCount, boolean stampDetected,
                                      boolean signatureDetected, double overallConfidence,
                                      ReviewStatus status, UUID reviewerId, Instant reviewedAt,
                                      Instant createdAt, List<FieldReview> fields,
                                      List<AuthorityItem> authorityItems, int lastAuditSequence) {
        OcrReview r = new OcrReview(reviewId, documentId, tenantId, documentName, pageCount,
                stampDetected, signatureDetected, overallConfidence, status, reviewerId, reviewedAt,
                createdAt, lastAuditSequence);
        if (fields != null) r.fields.addAll(fields);
        if (authorityItems != null) r.authorityItems.addAll(authorityItems);
        return r;
    }

    // ---- The review operations -----------------------------------------------------------------

    /**
     * Records a reviewer's decision on one field, writes append-only audit history, and raises the
     * matching events. The first decision moves the review PENDING → IN_PROGRESS.
     *
     * @throws ReviewAuthorityException        when a non-human (SYSTEM) actor attempts a review
     * @throws IllegalReviewTransitionException when the review is already terminal (VERIFIED)
     * @throws FieldNotFoundException          when the field is not part of this review
     * @throws ReviewInvariantViolationException on a rule violation (auto-accept on LOW, reject w/o reason, …)
     */
    public void reviewField(FieldId fieldId, FieldDecision decision, String correctedValue,
                            String reason, Reviewer reviewer, CorrelationId correlationId, TraceId traceId) {
        requireHuman(reviewer);
        if (status.isTerminal()) {
            throw new IllegalReviewTransitionException(
                    "Review " + reviewId + " is VERIFIED — its fields can no longer be changed");
        }

        FieldReview field = fieldById(fieldId);
        String previousValue = field.effectiveValue();

        switch (decision) {
            case AUTO_ACCEPTED   -> field.autoAccept(reviewer);
            case MANUAL_ACCEPTED -> field.manualAccept(reviewer);
            case CORRECTED       -> field.correct(correctedValue, reviewer);
            case REJECTED        -> field.reject(reason, reviewer);
            case NEEDS_REVIEW    -> field.flagNeedsReview(reviewer);
        }

        if (status == ReviewStatus.PENDING) {
            this.status = ReviewStatus.IN_PROGRESS;   // legal edge; first action opens the review
        }
        stamp(reviewer);

        recordAudit(field, decision, previousValue, decision == FieldDecision.REJECTED ? reason : null, reviewer);

        raise(new FieldReviewed(reviewId, documentId, tenantId, fieldId, field.fieldName(),
                decision, reviewer.userId(), correlationId, traceId));
        if (decision == FieldDecision.REJECTED) {
            raise(new ReviewRejected(reviewId, documentId, tenantId, fieldId, field.rejectionReason(),
                    reviewer.userId(), correlationId, traceId));
        }
        enforceInvariants();
    }

    /**
     * Moves the review through its lifecycle. The single status-mutating operation.
     *
     * @throws IllegalReviewTransitionException when the edge is not in the state machine
     * @throws ReviewInvariantViolationException when completing with fields still undecided
     * @throws ReviewAuthorityException          when the actor's role may not verify
     */
    public void changeStatus(ReviewStatus target, Reviewer reviewer,
                             CorrelationId correlationId, TraceId traceId) {
        requireHuman(reviewer);

        if (!ReviewStatusMachine.isLegal(status, target)) {
            throw IllegalReviewTransitionException.of(status, target);
        }

        if (target == ReviewStatus.REVIEW_COMPLETED) {
            long undecided = fields.stream().filter(f -> !f.isSettled()).count();
            if (undecided > 0) {
                throw new ReviewInvariantViolationException(
                        "Cannot complete review " + reviewId + " — " + undecided
                                + " field(s) still NEEDS_REVIEW");
            }
        }

        if (target == ReviewStatus.VERIFIED && !VERIFY_ROLES.contains(reviewer.role())) {
            throw new ReviewAuthorityException(
                    "Role " + reviewer.role() + " may not VERIFY a review (allowed: " + VERIFY_ROLES + ")");
        }

        this.status = target;
        stamp(reviewer);
        enforceInvariants();

        switch (target) {
            case REVIEW_COMPLETED -> raise(new ReviewCompleted(reviewId, documentId, tenantId,
                    reviewer.userId(), correlationId, traceId));
            case VERIFIED -> raise(new ReviewVerified(reviewId, documentId, tenantId,
                    reviewer.userId(), correlationId, traceId));
            default -> { /* IN_PROGRESS carries no dedicated event */ }
        }
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (reviewId == null)   throw new ReviewInvariantViolationException("reviewId is required");
        if (documentId == null) throw new ReviewInvariantViolationException("documentId is required");
        if (tenantId == null)   throw new ReviewInvariantViolationException("tenantId is required");
        if (status == null)     throw new ReviewInvariantViolationException("status is required");
        if (pageCount < 1)      throw new ReviewInvariantViolationException("pageCount must be >= 1");
        if (overallConfidence < 0.0 || overallConfidence > 1.0) {
            throw new ReviewInvariantViolationException("overallConfidence must be within [0,1]");
        }
        // A completed/verified review must have no undecided field.
        if ((status == ReviewStatus.REVIEW_COMPLETED || status == ReviewStatus.VERIFIED)
                && fields.stream().anyMatch(f -> !f.isSettled())) {
            throw new ReviewInvariantViolationException(
                    "Review " + reviewId + " is " + status + " but has undecided fields");
        }
    }

    private void requireHuman(Reviewer reviewer) {
        if (reviewer == null || reviewer.isSystem()) {
            throw new ReviewAuthorityException("A review action requires a human reviewer, not the system");
        }
    }

    private void stamp(Reviewer reviewer) {
        this.reviewerId = reviewer.userId();
        this.reviewedAt = Instant.now();
    }

    private void recordAudit(FieldReview field, FieldDecision decision, String previousValue,
                             String reason, Reviewer reviewer) {
        this.lastAuditSequence += 1;
        recordedAudits.add(FieldAuditEntry.record(field.fieldId(), decision, previousValue,
                field.effectiveValue(), reason, reviewer, lastAuditSequence));
    }

    private FieldReview fieldById(FieldId fieldId) {
        return fields.stream()
                .filter(f -> f.fieldId().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new FieldNotFoundException(
                        "Field " + fieldId + " is not part of review " + reviewId));
    }

    // ---- Progress queries ----------------------------------------------------------------------

    public int totalFields()     { return fields.size(); }
    public long acceptedCount()  { return fields.stream().filter(f -> f.decision().isAccepted() && f.decision() != FieldDecision.CORRECTED).count(); }
    public long correctedCount() { return countByDecision(FieldDecision.CORRECTED); }
    public long rejectedCount()  { return countByDecision(FieldDecision.REJECTED); }
    public long remainingCount() { return fields.stream().filter(f -> !f.isSettled()).count(); }

    private long countByDecision(FieldDecision decision) {
        return fields.stream().filter(f -> f.decision() == decision).count();
    }

    // ---- Accessors -----------------------------------------------------------------------------

    /** Audit entries recorded this unit of work; drained once by the repository after save. */
    public List<FieldAuditEntry> pullAuditEntries() {
        List<FieldAuditEntry> drained = List.copyOf(recordedAudits);
        recordedAudits.clear();
        return drained;
    }

    public ReviewId reviewId()          { return reviewId; }
    public UUID documentId()            { return documentId; }
    public UUID tenantId()              { return tenantId; }
    public String documentName()        { return documentName; }
    public int pageCount()              { return pageCount; }
    public boolean stampDetected()      { return stampDetected; }
    public boolean signatureDetected()  { return signatureDetected; }
    public double overallConfidence()   { return overallConfidence; }
    public ReviewStatus status()        { return status; }
    public UUID reviewerId()            { return reviewerId; }
    public Instant reviewedAt()         { return reviewedAt; }
    public Instant createdAt()          { return createdAt; }
    public int lastAuditSequence()      { return lastAuditSequence; }
    public List<FieldReview> fields()               { return Collections.unmodifiableList(fields); }
    public List<AuthorityItem> authorityItems()     { return Collections.unmodifiableList(authorityItems); }
}
