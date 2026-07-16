package com.notarist.review.api.response;

import com.notarist.review.domain.model.AuthorityItem;
import com.notarist.review.domain.state.AuthorityDecision;

/**
 * One row of the "Timeline Direksi" (authority approval trail). Shape matches what the existing
 * DirectorTimeline component reads: {@code id, role, name, decision, at}. Decision is mapped to the
 * frontend's vocabulary (CONFIRMED → APPROVED).
 */
public record AuthorityTimelineEntryResponse(
        String id,
        String role,
        String name,
        String decision,   // PENDING|APPROVED|REJECTED
        String at,
        // ---- additive ----
        String authorityType,
        String content,
        Double confidence
) {
    public static AuthorityTimelineEntryResponse from(AuthorityItem a) {
        return new AuthorityTimelineEntryResponse(
                a.authorityId().toString(),
                a.roleLabel(),
                a.personName(),
                frontendDecision(a.decision()),
                a.decidedAt() != null ? a.decidedAt().toString() : null,
                a.type().name(),
                a.content(),
                a.confidence());
    }

    private static String frontendDecision(AuthorityDecision d) {
        return switch (d) {
            case CONFIRMED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case PENDING -> "PENDING";
        };
    }
}
