package com.notarist.verification.domain.model;

import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.ItemId;
import com.notarist.verification.domain.valueobject.Reviewer;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line in a checklist item's decision history. Append-only: an entry is created for
 * every decision and is never updated or deleted. Preserves the decision that stood before, so a
 * changed decision is fully reconstructable from history alone.
 */
public final class ItemAuditEntry {

    private final UUID auditId;
    private final ItemId itemId;
    private final Decision decision;
    private final Decision previousDecision;
    private final String comment;
    private final UUID reviewerId;
    private final String reviewerRole;
    private final Instant occurredAt;
    private final int sequence;

    private ItemAuditEntry(UUID auditId, ItemId itemId, Decision decision, Decision previousDecision,
                           String comment, UUID reviewerId, String reviewerRole, Instant occurredAt,
                           int sequence) {
        this.auditId = auditId;
        this.itemId = itemId;
        this.decision = decision;
        this.previousDecision = previousDecision;
        this.comment = comment;
        this.reviewerId = reviewerId;
        this.reviewerRole = reviewerRole;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    static ItemAuditEntry record(ItemId itemId, Decision decision, Decision previousDecision,
                                 String comment, Reviewer reviewer, int sequence) {
        return new ItemAuditEntry(UUID.randomUUID(), itemId, decision, previousDecision, comment,
                reviewer.userId(), reviewer.role().name(), Instant.now(), sequence);
    }

    public static ItemAuditEntry rehydrate(UUID auditId, ItemId itemId, Decision decision,
                                           Decision previousDecision, String comment, UUID reviewerId,
                                           String reviewerRole, Instant occurredAt, int sequence) {
        return new ItemAuditEntry(auditId, itemId, decision, previousDecision, comment, reviewerId,
                reviewerRole, occurredAt, sequence);
    }

    public UUID auditId()               { return auditId; }
    public ItemId itemId()              { return itemId; }
    public Decision decision()          { return decision; }
    public Decision previousDecision()  { return previousDecision; }
    public String comment()             { return comment; }
    public UUID reviewerId()            { return reviewerId; }
    public String reviewerRole()        { return reviewerRole; }
    public Instant occurredAt()         { return occurredAt; }
    public int sequence()               { return sequence; }
}
