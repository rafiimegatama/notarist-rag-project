package com.notarist.kase.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic reminder feed, grouped by due-date proximity. No LLM, no inference — every item's due
 * date is computed from the case's own fields and a fixed rule set.
 */
@Schema(description = "Case reminders grouped by deadline proximity")
public record ReminderResponse(
        String generatedAt,
        int totalCount,
        @Schema(description = "Item count per bucket: TODAY, WITHIN_7_DAYS, WITHIN_30_DAYS, OVERDUE")
        Map<String, Integer> countsByBucket,
        List<ReminderItem> overdue,
        List<ReminderItem> today,
        List<ReminderItem> within7Days,
        List<ReminderItem> within30Days
) {
    @Schema(description = "One reminder for one case")
    public record ReminderItem(
            UUID caseId,
            String caseNumber,
            String caseType,
            String state,
            @Schema(description = "SKMHT_DEADLINE | APHT_DEADLINE | PENDING_VERIFICATION | PENDING_QC | PENDING_APPROVAL")
            String reminderType,
            String dueDate,
            @Schema(description = "Whole days until due; negative when overdue")
            long daysUntilDue
    ) {}
}
