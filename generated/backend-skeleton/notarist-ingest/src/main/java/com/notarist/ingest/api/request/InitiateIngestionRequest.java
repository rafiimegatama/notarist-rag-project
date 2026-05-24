package com.notarist.ingest.api.request;

import jakarta.validation.constraints.*;

public record InitiateIngestionRequest(
    @NotBlank @Size(min = 1, max = 255) String originalFilename,
    @NotBlank String documentType,
    String jenisAkta,
    @NotBlank @Pattern(regexp = "application/pdf") String mimeType,
    @NotNull @Min(1) @Max(52428800) Long fileSizeBytes,
    @NotBlank String classificationLevel,
    @NotBlank @Size(min = 64, max = 64) String checksumSha256
) {}
