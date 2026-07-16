package com.notarist.kase.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code PATCH /api/v1/cases/{id}/status}. */
@Schema(description = "Requests a case status transition")
public record ChangeCaseStatusRequest(

        @Schema(description = "Target CaseState. Must be a legal edge from the current state.",
                example = "UPLOADING", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "targetState is required")
        String targetState,

        @Schema(description = "Why the case moved. Mandatory for ROLLBACK/CANCEL transitions.")
        String reason
) {}
