package com.notarist.verification.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /bundles/{id}/verification/checklist/{itemId}}.
 *
 * <p>{@code decision} accepts the frontend vocabulary (APPROVED | REJECTED | NEEDS_CHECK) or a domain
 * {@code Decision} name. {@code comment} carries the reason (mandatory when the decision is FAIL).
 */
public record UpdateChecklistItemRequest(
        @NotBlank String decision,
        String comment
) {}
