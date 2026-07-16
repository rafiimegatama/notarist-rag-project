package com.notarist.verification.domain.model;

import com.notarist.verification.domain.exception.VerificationInvariantViolationException;
import com.notarist.verification.domain.state.ChecklistItemStatus;
import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.CheckType;
import com.notarist.verification.domain.valueobject.ChecklistCategory;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Reviewer;

import java.time.Instant;
import java.util.UUID;

/**
 * One checklist item and the verifier's decision on it. A child entity of {@link Verification}: its
 * mutator is package-visible, so the aggregate is the only thing that can settle an item — a service
 * cannot reach in and record a decision past the rules.
 *
 * <p>Business rule enforced here: a {@code FAIL} decision requires a non-blank reason (comment).
 */
public final class ChecklistItem {

    private final ItemId itemId;
    private final ChecklistCategory category;
    private final String title;
    private final boolean mandatory;
    private final CheckType checkType;
    private final int sortOrder;

    private ChecklistItemStatus status;
    private Decision decision;
    private UUID reviewerId;
    private Instant reviewedAt;
    private String comment;

    private ChecklistItem(ItemId itemId, ChecklistCategory category, String title, boolean mandatory,
                          CheckType checkType, int sortOrder, ChecklistItemStatus status,
                          Decision decision, UUID reviewerId, Instant reviewedAt, String comment) {
        this.itemId = itemId;
        this.category = category;
        this.title = title;
        this.mandatory = mandatory;
        this.checkType = checkType;
        this.sortOrder = sortOrder;
        this.status = status;
        this.decision = decision;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.comment = comment;
    }

    /** A fresh, undecided item. */
    public static ChecklistItem create(ItemId itemId, ChecklistCategory category, String title,
                                       boolean mandatory, CheckType checkType, int sortOrder) {
        if (title == null || title.isBlank()) {
            throw new VerificationInvariantViolationException("checklist item title is required");
        }
        return new ChecklistItem(itemId, category, title, mandatory, checkType, sortOrder,
                ChecklistItemStatus.PENDING, null, null, null, null);
    }

    /**
     * A fresh item that already carries an automatic-check outcome (from OCR-review output). It counts
     * as decided (COMPLETED) but records no human reviewer — a human may still override it later.
     */
    public static ChecklistItem automatic(ItemId itemId, ChecklistCategory category, String title,
                                          boolean mandatory, int sortOrder, Decision decision,
                                          String comment) {
        ChecklistItem item = create(itemId, category, title, mandatory, CheckType.AUTOMATIC, sortOrder);
        if (decision != null) {
            if (decision == Decision.FAIL && (comment == null || comment.isBlank())) {
                throw new VerificationInvariantViolationException(
                        "automatic FAIL of '" + title + "' must record a reason");
            }
            item.decision = decision;
            item.comment = comment;
            item.status = ChecklistItemStatus.COMPLETED;
        }
        return item;
    }

    /** Rehydration from persistence — no rules re-applied, the row is already valid. */
    public static ChecklistItem rehydrate(ItemId itemId, ChecklistCategory category, String title,
                                          boolean mandatory, CheckType checkType, int sortOrder,
                                          ChecklistItemStatus status, Decision decision, UUID reviewerId,
                                          Instant reviewedAt, String comment) {
        return new ChecklistItem(itemId, category, title, mandatory, checkType, sortOrder, status,
                decision, reviewerId, reviewedAt, comment);
    }

    // ---- Mutator — package-visible, driven only by Verification ---------------------------------

    void decide(Decision decision, String comment, Reviewer reviewer) {
        if (decision == Decision.FAIL && (comment == null || comment.isBlank())) {
            throw new VerificationInvariantViolationException(
                    "Failing '" + title + "' requires a reason (comment)");
        }
        this.decision = decision;
        this.comment = comment;
        this.reviewerId = reviewer.userId();
        this.reviewedAt = Instant.now();
        this.status = ChecklistItemStatus.COMPLETED;
    }

    // ---- Queries --------------------------------------------------------------------------------

    public boolean isSettled()  { return status == ChecklistItemStatus.COMPLETED; }
    public boolean isBlocking() { return decision == Decision.MANUAL_REQUIRED; }

    public ItemId itemId()               { return itemId; }
    public ChecklistCategory category()  { return category; }
    public String title()                { return title; }
    public boolean mandatory()           { return mandatory; }
    public CheckType checkType()         { return checkType; }
    public int sortOrder()               { return sortOrder; }
    public ChecklistItemStatus status()  { return status; }
    public Decision decision()           { return decision; }
    public UUID reviewerId()             { return reviewerId; }
    public Instant reviewedAt()          { return reviewedAt; }
    public String comment()              { return comment; }
}
