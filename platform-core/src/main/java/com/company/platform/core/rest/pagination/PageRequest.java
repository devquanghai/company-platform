package com.company.platform.core.rest.pagination;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PageRequest {

    int page;
    int size;
    List<SortOrder> sorts;

    public PageRequest(
        int page,
        int size,
        List<SortOrder> sorts
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

        this.page = page;
        this.size = size;
        this.sorts = List.copyOf(
            sorts == null ? List.of() : sorts
        );
    }

    public static PageRequest of(
        int page,
        int size
    ) {
        return new PageRequest(
            page,
            size,
            List.of()
        );
    }

    public static PageRequest of(
        int page,
        int size,
        List<SortOrder> sorts
    ) {
        return new PageRequest(
            page,
            size,
            sorts
        );
    }

    public long getOffset() {
        return (long) page * size;
    }

    public boolean hasSort() {
        return !sorts.isEmpty();
    }
}
