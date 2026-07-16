package com.notarist.review.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code PUT /documents/{id}/ocr/fields/{fieldId}}.
 *
 * <p>{@code decision} accepts the frontend vocabulary (APPROVED | REJECTED | NEEDS_CHECK) or a domain
 * {@code FieldDecision} name. {@code value} carries a correction; {@code reason} carries a rejection
 * reason.
 */
public record ReviewFieldRequest(
        @NotBlank String decision,
        String value,
        String reason
) {}
