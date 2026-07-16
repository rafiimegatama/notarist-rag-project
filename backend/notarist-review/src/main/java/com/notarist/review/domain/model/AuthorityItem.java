package com.notarist.review.domain.model;

import com.notarist.review.domain.state.AuthorityDecision;
import com.notarist.review.domain.valueobject.AuthorityId;
import com.notarist.review.domain.valueobject.AuthorityType;
import com.notarist.review.domain.valueobject.Reviewer;

import java.time.Instant;

/**
 * A read-only authority extraction (authority clause, director timeline, current directors, signing
 * authority). The extracted content is never edited by a reviewer — the only mutation is to confirm
 * or reject it. A child entity of {@link OcrReview}.
 */
public final class AuthorityItem {

    private final AuthorityId authorityId;
    private final AuthorityType type;
    private final String roleLabel;
    private final String personName;
    private final String content;
    private final Double confidence;
    private final int sortOrder;

    private AuthorityDecision decision;
    private Instant decidedAt;

    private AuthorityItem(AuthorityId authorityId, AuthorityType type, String roleLabel,
                          String personName, String content, Double confidence, int sortOrder,
                          AuthorityDecision decision, Instant decidedAt) {
        this.authorityId = authorityId;
        this.type = type;
        this.roleLabel = roleLabel;
        this.personName = personName;
        this.content = content;
        this.confidence = confidence;
        this.sortOrder = sortOrder;
        this.decision = decision;
        this.decidedAt = decidedAt;
    }

    public static AuthorityItem extracted(AuthorityId authorityId, AuthorityType type, String roleLabel,
                                          String personName, String content, Double confidence,
                                          int sortOrder) {
        return new AuthorityItem(authorityId, type, roleLabel, personName, content, confidence,
                sortOrder, AuthorityDecision.PENDING, null);
    }

    public static AuthorityItem rehydrate(AuthorityId authorityId, AuthorityType type, String roleLabel,
                                          String personName, String content, Double confidence,
                                          int sortOrder, AuthorityDecision decision, Instant decidedAt) {
        return new AuthorityItem(authorityId, type, roleLabel, personName, content, confidence,
                sortOrder, decision, decidedAt);
    }

    void confirm(Reviewer reviewer) {
        this.decision = AuthorityDecision.CONFIRMED;
        this.decidedAt = Instant.now();
    }

    void reject(Reviewer reviewer) {
        this.decision = AuthorityDecision.REJECTED;
        this.decidedAt = Instant.now();
    }

    public AuthorityId authorityId()    { return authorityId; }
    public AuthorityType type()         { return type; }
    public String roleLabel()           { return roleLabel; }
    public String personName()          { return personName; }
    public String content()             { return content; }
    public Double confidence()          { return confidence; }
    public int sortOrder()              { return sortOrder; }
    public AuthorityDecision decision() { return decision; }
    public Instant decidedAt()          { return decidedAt; }
}
