package com.notarist.verification.api.response;

import com.notarist.verification.domain.model.ChecklistItem;

/**
 * Read model for one checklist item. Carries the domain decision (PASS/FAIL/NOT_APPLICABLE/
 * MANUAL_REQUIRED), the item status, the reviewer and the comment — the fields a verification UI row
 * renders.
 */
public record ChecklistItemResponse(
        String id,
        String category,
        String title,
        boolean mandatory,
        String checkType,
        String status,       // PENDING|COMPLETED
        String decision,     // PASS|FAIL|NOT_APPLICABLE|MANUAL_REQUIRED, or null while PENDING
        String reviewer,
        String reviewedAt,
        String comment
) {
    public static ChecklistItemResponse from(ChecklistItem i) {
        return new ChecklistItemResponse(
                i.itemId().toString(),
                i.category().name(),
                i.title(),
                i.mandatory(),
                i.checkType().name(),
                i.status().name(),
                i.decision() != null ? i.decision().name() : null,
                i.reviewerId() != null ? i.reviewerId().toString() : null,
                i.reviewedAt() != null ? i.reviewedAt().toString() : null,
                i.comment());
    }
}
