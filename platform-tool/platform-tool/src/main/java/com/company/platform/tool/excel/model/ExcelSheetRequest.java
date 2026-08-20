package com.company.platform.tool.excel.model;

import java.util.List;
import java.util.Objects;

public record ExcelSheetRequest<T>(String name, Iterable<T> rows, List<ExcelColumn<T>> columns, String title,
                                   List<ExcelMergeRegion> merges, boolean freezeHeader, boolean autoFilter,
                                   boolean streaming, int maximumRows) {
    public ExcelSheetRequest {
        Objects.requireNonNull(rows, "rows");
        columns = List.copyOf(columns);
        merges = merges == null ? List.of() : List.copyOf(merges);
        if (name == null || name.isBlank() || columns.isEmpty())
            throw new IllegalArgumentException("sheet name and columns are required");
        maximumRows = maximumRows < 1 ? 1_000_000 : maximumRows;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private String name;
        private Iterable<T> rows = List.of();
        private List<ExcelColumn<T>> columns = List.of();
        private String title;
        private List<ExcelMergeRegion> merges = List.of();
        private boolean freezeHeader = true;
        private boolean autoFilter;
        private boolean streaming;
        private int maximumRows = 1_000_000;

        public Builder<T> name(String value) {
            name = value;
            return this;
        }

        public Builder<T> rows(Iterable<T> value) {
            rows = value;
            return this;
        }

        public Builder<T> columns(List<ExcelColumn<T>> value) {
            columns = value;
            return this;
        }

        public Builder<T> title(String value) {
            title = value;
            return this;
        }

        public Builder<T> merges(List<ExcelMergeRegion> value) {
            merges = value;
            return this;
        }

        public Builder<T> freezeHeader(boolean value) {
            freezeHeader = value;
            return this;
        }

        public Builder<T> autoFilter(boolean value) {
            autoFilter = value;
            return this;
        }

        public Builder<T> streaming(boolean value) {
            streaming = value;
            return this;
        }

        public Builder<T> maximumRows(int value) {
            maximumRows = value;
            return this;
        }

        public ExcelSheetRequest<T> build() {
            return new ExcelSheetRequest<>(name, rows, columns, title, merges, freezeHeader, autoFilter, streaming, maximumRows);
        }
    }
}
