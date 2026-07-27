package com.company.platform.core.rest.pagination;

import com.company.platform.core.exception.PlatformValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void pageRequestSupportsDefaultsSortingOffsetAndValueSemantics() {
        PageRequest first = PageRequest.of(2, 25);
        SortOrder descending = new SortOrder("createdAt", SortDirection.DESC);
        List<SortOrder> mutableSorts = new ArrayList<>(List.of(descending));
        PageRequest sorted = PageRequest.of(1, 10, mutableSorts);
        PageRequest equivalent = new PageRequest(1, 10, List.of(descending));
        mutableSorts.clear();

        assertThat(first.getPage()).isEqualTo(2);
        assertThat(first.getSize()).isEqualTo(25);
        assertThat(first.getOffset()).isEqualTo(50);
        assertThat(first.hasSort()).isFalse();
        assertThat(sorted.hasSort()).isTrue();
        assertThat(sorted.getSorts()).containsExactly(descending);
        assertThat(sorted).isEqualTo(equivalent).hasSameHashCodeAs(equivalent);
        assertThat(sorted.toString()).contains("page=1", "size=10", "createdAt");
    }

    @Test
    void pageRequestRejectsInvalidBoundsAndTreatsNullSortsAsEmpty() {
        assertThatThrownBy(() -> PageRequest.of(-1, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be zero or positive");
        assertThatThrownBy(() -> PageRequest.of(0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("size must be positive");

        assertThat(new PageRequest(0, 1, null).getSorts()).isEmpty();
    }

    @Test
    void sortOrderValidatesPropertyAndDefaultsDirection() {
        SortOrder defaultDirection = new SortOrder("name", null);

        assertThat(defaultDirection.getDirection()).isEqualTo(SortDirection.ASC);
        assertThat(defaultDirection.getProperty()).isEqualTo("name");
        assertThat(SortDirection.values()).containsExactly(SortDirection.ASC, SortDirection.DESC);
        assertThatThrownBy(() -> new SortOrder(null, SortDirection.ASC))
            .isInstanceOf(PlatformValidationException.class)
            .hasMessage("Property cannot be null or blank");
        assertThatThrownBy(() -> new SortOrder(" ", SortDirection.ASC))
            .isInstanceOf(PlatformValidationException.class)
            .hasMessage("Property cannot be null or blank");
    }

    @Test
    void pageResultCalculatesPageStateAndDefensivelyCopiesContent() {
        List<String> mutableContent = new ArrayList<>(List.of("a", "b"));
        PageResult<String> first = PageResult.of(mutableContent, 0, 2, 5);
        PageResult<String> middle = PageResult.of(List.of("c", "d"), 1, 2, 5);
        PageResult<String> last = PageResult.of(List.of("e"), 2, 2, 5);
        mutableContent.clear();

        assertThat(first)
            .extracting("content", "page", "size", "totalElements", "totalPages", "first", "last")
            .containsExactly(List.of("a", "b"), 0, 2, 5L, 3, true, false);
        assertThat(middle)
            .extracting("first", "last")
            .containsExactly(false, false);
        assertThat(last)
            .extracting("first", "last")
            .containsExactly(false, true);
    }

    @Test
    void emptyPageAndNullContentAreSupported() {
        assertThat(PageResult.empty(0, 10))
            .extracting("content", "totalPages", "first", "last")
            .containsExactly(List.of(), 0, true, true);
        assertThat(PageResult.of(null, 1, 10, 0))
            .extracting("content", "first", "last")
            .containsExactly(List.of(), false, true);
    }

    @Test
    void pageResultRejectsInvalidBoundsAndUnsupportedPageCount() {
        assertThatThrownBy(() -> PageResult.empty(-1, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be zero or positive");
        assertThatThrownBy(() -> PageResult.empty(0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("size must be positive");
        assertThatThrownBy(() -> PageResult.of(List.of(), 0, 1, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("totalElements must be zero or positive");
        assertThatThrownBy(() -> PageResult.of(
            List.of(),
            0,
            2,
            ((long) Integer.MAX_VALUE + 1) * 2
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("totalPages exceeds the supported integer range");
    }
}
