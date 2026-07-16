package com.notarist.review.api.request;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PATCH /documents/{id}/ocr/status}. {@code targetStatus} is a {@code ReviewStatus} name. */
public record ChangeReviewStatusRequest(
        @NotBlank String targetStatus
) {}
