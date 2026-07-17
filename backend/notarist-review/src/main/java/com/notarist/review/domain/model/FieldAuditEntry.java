package com.notarist.review.domain.model;

import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.valueobject.FieldId;
import com.notarist.review.domain.valueobject.Reviewer;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line in a field's decision history. Append-only: an entry is created for every field
 * decision and is never updated or deleted. Preserves the value as it was BEFORE the decision and the
 * value AFTER, so a corrected field's original can always be recovered from history alone.
 */
public final class FieldAuditEntry {

    private final UUID auditId;
    private final FieldId fieldId;
    private final FieldDecision decision;
    private final String previousValue;
    private final String newValue;
    private final String reason;
    private final UUID reviewerId;
    private final String reviewerRole;
    private final Instant occurredAt;
    private final int sequence;

    private FieldAuditEntry(UUID auditId, FieldId fieldId, FieldDecision decision, String previousValue,
                            String newValue, String reason, UUID reviewerId, String reviewerRole,
                            Instant occurredAt, int sequence) {
        this.auditId = auditId;
        this.fieldId = fieldId;
        this.decision = decision;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.reason = reason;
        this.reviewerId = reviewerId;
        this.reviewerRole = reviewerRole;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    /** A freshly recorded decision. */
    static FieldAuditEntry record(FieldId fieldId, FieldDecision decision, String previousValue,
                                  String newValue, String reason, Reviewer reviewer, int sequence) {
        return new FieldAuditEntry(UUID.randomUUID(), fieldId, decision, previousValue, newValue,
                reason, reviewer.userId(), reviewer.role().name(), Instant.now(), sequence);
    }

    /** Rehydration from persistence. */
    public static FieldAuditEntry rehydrate(UUID auditId, FieldId fieldId, FieldDecision decision,
                                            String previousValue, String newValue, String reason,
                                            UUID reviewerId, String reviewerRole, Instant occurredAt,
                                            int sequence) {
        return new FieldAuditEntry(auditId, fieldId, decision, previousValue, newValue, reason,
                reviewerId, reviewerRole, occurredAt, sequence);
    }

    public UUID auditId()          { return auditId; }
    public FieldId fieldId()       { return fieldId; }
    public FieldDecision decision() { return decision; }
    public String previousValue()  { return previousValue; }
    public String newValue()       { return newValue; }
    public String reason()         { return reason; }
    public UUID reviewerId()       { return reviewerId; }
    public String reviewerRole()   { return reviewerRole; }
    public Instant occurredAt()    { return occurredAt; }
    public int sequence()          { return sequence; }
}
