package com.notarist.kase.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body for {@code PATCH /api/v1/bundles/{bundleId}/status}. */
@Schema(description = "Requests a bundle workflow transition")
public record ChangeBundleStatusRequest(

        @Schema(description = "Target BundleWorkflowStatus; must be a legal edge from the current status",
                example = "COLLECTING_DOCUMENTS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "targetStatus is required")
        String targetStatus
) {}
