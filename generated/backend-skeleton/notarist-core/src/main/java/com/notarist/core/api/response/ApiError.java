package com.notarist.core.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code,
    String message,
    List<ApiErrorDetail> details
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, List<ApiErrorDetail> details) {
        return new ApiError(code, message, details == null ? null : List.copyOf(details));
    }
}
