package com.notarist.verification.api.request;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PATCH /bundles/{id}/verification/status}. {@code targetStatus} is a {@code VerificationStatus} name. */
public record ChangeVerificationStatusRequest(
        @NotBlank String targetStatus
) {}
