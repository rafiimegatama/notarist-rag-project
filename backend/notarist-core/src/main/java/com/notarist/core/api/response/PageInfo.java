package com.notarist.core.api.response;

public record PageInfo(
    int number,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,
    boolean isFirst,
    boolean isLast
) {
    public static PageInfo of(int number, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageInfo(
            number, size, totalElements, totalPages,
            number < totalPages - 1,
            number > 0,
            number == 0,
            number >= totalPages - 1
        );
    }
}
