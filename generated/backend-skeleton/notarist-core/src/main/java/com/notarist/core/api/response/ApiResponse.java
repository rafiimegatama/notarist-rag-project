package com.notarist.core.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API response envelope for all NOTARIST RAG Platform responses.
 * Contract: { meta, data, error } — meta always present; data/error are mutually exclusive.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    ApiMeta meta,
    T data,
    ApiError error
) {
    public static <T> ApiResponse<T> success(ApiMeta meta, T data) {
        return new ApiResponse<>(meta, data, null);
    }

    public static <T> ApiResponse<T> error(ApiMeta meta, ApiError error) {
        return new ApiResponse<>(meta, null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
