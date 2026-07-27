package com.company.platform.core.rest.pagination;

import java.util.List;

public final class PageResult<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public PageResult(
        List<T> content,
        int page,
        int size,
        long totalElements
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                "page must be zero or positive"
            );
        }

        if (size <= 0) {
            throw new IllegalArgumentException(
                "size must be positive"
            );
        }

        if (totalElements < 0) {
            throw new IllegalArgumentException(
                "totalElements must be zero or positive"
            );
        }

        this.content = List.copyOf(
            content == null ? List.of() : content
        );
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = calculateTotalPages(
            totalElements,
            size
        );
        this.first = page == 0;
        this.last = totalPages == 0
            || page >= totalPages - 1;
    }

    public static <T> PageResult<T> of(
        List<T> content,
        int page,
        int size,
        long totalElements
    ) {
        return new PageResult<>(
            content,
            page,
            size,
            totalElements
        );
    }

    public static <T> PageResult<T> empty(
        int page,
        int size
    ) {
        return new PageResult<>(
            List.of(),
            page,
            size,
            0
        );
    }

    private static int calculateTotalPages(
        long totalElements,
        int size
    ) {
        if (totalElements == 0) {
            return 0;
        }

        long pages = (totalElements + size - 1) / size;

        if (pages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "totalPages exceeds the supported integer range"
            );
        }

        return (int) pages;
    }
}
