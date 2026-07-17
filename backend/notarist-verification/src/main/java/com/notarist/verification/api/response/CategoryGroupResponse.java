package com.notarist.verification.api.response;

import java.util.List;

/** Checklist items grouped by category — what the verification screen renders as sections. */
public record CategoryGroupResponse(
        String category,
        List<ChecklistItemResponse> items
) {}
