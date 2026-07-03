package com.notarist.core.api.response;

/** Standardised JSON response envelope for all Notarist API endpoints. */
public record ApiResponse<T>(
        String status,
        ApiMeta meta,
        T data,
        String errorCode,
        String errorMessage
) {
    public static <T> ApiResponse<T> success(ApiMeta meta, T data) {
        return new ApiResponse<>("SUCCESS", meta, data, null, null);
    }

    public static <T> ApiResponse<T> error(ApiMeta meta, String errorCode, String errorMessage) {
        return new ApiResponse<>("ERROR", meta, null, errorCode, errorMessage);
    }

    public static <T> ApiResponse<T> error(ApiMeta meta, ApiError error) {
        return new ApiResponse<>("ERROR", meta, null, error.code(), error.message());
    }
}
