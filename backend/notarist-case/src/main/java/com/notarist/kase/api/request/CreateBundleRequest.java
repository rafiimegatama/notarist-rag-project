package com.notarist.kase.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Body for {@code POST /api/v1/cases/{caseId}/bundles}. */
@Schema(description = "Opens a new bundle on a case")
public record CreateBundleRequest(

        @Schema(description = "What the bundle is for", example = "IDENTITY",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"IDENTITY", "LAND_CERTIFICATE", "SUPPORTING", "DRAFT_OUTPUT"})
        @NotBlank(message = "bundleType is required")
        String bundleType,

        @Schema(description = "How many documents the bundle is expected to hold (0 if unknown)")
        @PositiveOrZero(message = "expectedDocumentCount must be >= 0")
        int expectedDocumentCount
) {}
