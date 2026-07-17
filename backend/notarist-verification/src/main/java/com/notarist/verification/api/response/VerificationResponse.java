package com.notarist.verification.api.response;

import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read model for {@code GET /bundles/{id}/verification}. Carries everything the VerificationScreen
 * needs: status, progress, the flat checklist, the checklist grouped by category, a summary, and the
 * reviewer/review state.
 */
public record VerificationResponse(
        UUID bundleId,
        UUID verificationId,
        String status,
        UUID reviewerId,
        String reviewedAt,
        VerificationProgressResponse progress,
        VerificationSummaryResponse summary,
        List<ChecklistItemResponse> checklist,
        List<CategoryGroupResponse> categories
) {
    public static VerificationResponse from(Verification v) {
        List<ChecklistItemResponse> checklist = v.items().stream()
                .sorted(java.util.Comparator.comparingInt(ChecklistItem::sortOrder))
                .map(ChecklistItemResponse::from)
                .toList();

        // Group by category, preserving first-seen (sort) order.
        List<CategoryGroupResponse> categories = checklist.stream()
                .collect(Collectors.groupingBy(ChecklistItemResponse::category,
                        java.util.LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new CategoryGroupResponse(e.getKey(), e.getValue()))
                .toList();

        return new VerificationResponse(
                v.bundleId(),
                v.verificationId().value(),
                v.status().name(),
                v.reviewerId(),
                v.reviewedAt() != null ? v.reviewedAt().toString() : null,
                VerificationProgressResponse.from(v),
                VerificationSummaryResponse.from(v),
                checklist,
                categories);
    }
}
