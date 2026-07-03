package com.notarist.core.api.response;

public record ApiErrorDetail(
    String field,
    String issue
) {
    public static ApiErrorDetail of(String field, String issue) {
        return new ApiErrorDetail(field, issue);
    }
}
