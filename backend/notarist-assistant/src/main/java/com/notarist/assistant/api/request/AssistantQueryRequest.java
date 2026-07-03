package com.notarist.assistant.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantQueryRequest(
    @NotBlank @Size(min = 1, max = 1000) String queryText,
    Boolean stream,
    @Min(1) @Max(10) Integer topK,
    String searchIntent
) {
    public AssistantQueryRequest {
        stream = (stream == null) ? true : stream;
        topK = (topK == null) ? 5 : topK;
        searchIntent = (searchIntent == null) ? "HYBRID" : searchIntent;
    }
}
