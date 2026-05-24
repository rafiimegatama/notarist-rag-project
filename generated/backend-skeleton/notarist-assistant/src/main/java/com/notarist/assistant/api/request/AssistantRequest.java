package com.notarist.assistant.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST /api/v1/assistant/ask and /api/v1/assistant/ask/stream.
 *
 * PHASE 6A.3-FIX: aligned skeleton with phase4 implementation contract.
 * Replaces the draft AssistantQueryRequest (deprecated).
 *
 * Field notes:
 *   rawQuery                — user's question; required, 1–2000 chars
 *   maxClassificationLevel  — optional clearance filter (e.g. "INTERNAL", "CONFIDENTIAL")
 *   documentTypeFilter      — optional JenisDokumen filter (e.g. "AKTA_JUAL_BELI")
 *   safetyMode              — optional safety mode ("STRICT", "RELAXED"); default STRICT
 *   maxResults              — max context chunks to retrieve; default 10
 *   contextTokenBudget      — max tokens in assembled context; default 3072
 */
public record AssistantRequest(
        @NotBlank @Size(min = 1, max = 2000) String rawQuery,
        String maxClassificationLevel,
        String documentTypeFilter,
        String safetyMode,
        @Min(1) @Max(50) Integer maxResults,
        @Min(512) @Max(8192) Integer contextTokenBudget
) {
    public AssistantRequest {
        safetyMode = (safetyMode == null || safetyMode.isBlank()) ? "STRICT" : safetyMode;
        maxResults = (maxResults == null || maxResults < 1) ? 10 : maxResults;
        contextTokenBudget = (contextTokenBudget == null || contextTokenBudget < 512) ? 3072 : contextTokenBudget;
    }
}
