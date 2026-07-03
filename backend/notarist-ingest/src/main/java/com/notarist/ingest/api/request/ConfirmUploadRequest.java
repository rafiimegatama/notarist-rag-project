package com.notarist.ingest.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmUploadRequest(
    @NotBlank @Size(min = 64, max = 64)
    @Pattern(regexp = "^[a-fA-F0-9]{64}$")
    String checksumSha256
) {}
