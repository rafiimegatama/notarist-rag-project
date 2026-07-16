package com.notarist.kase.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Body for {@code POST /api/v1/cases}. */
@Schema(description = "Opens a new notarial case")
public record CreateCaseRequest(

        @Schema(description = "Tenant-unique human reference, format {nomor}/{bulan}/{tahun}",
                example = "12/V/2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "caseNumber is required")
        String caseNumber,

        @Schema(description = "Kind of notarial work",
                example = "APHT", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"APHT", "SKMHT", "AJB", "FIDUSIA", "ROYA", "WASIAT", "KUASA",
                        "PENDIRIAN_PT", "LAINNYA"})
        @NotBlank(message = "caseType is required")
        String caseType,

        @Schema(description = "Notary assigned to the case (optional at creation)")
        UUID assignedNotarisId
) {}
