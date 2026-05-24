package com.notarist.core.api.response;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    PageInfo page
) {
    public static <T> PageResponse<T> of(List<T> items, int pageNumber, int pageSize, long totalElements) {
        return new PageResponse<>(
            List.copyOf(items),
            PageInfo.of(pageNumber, pageSize, totalElements)
        );
    }
}
