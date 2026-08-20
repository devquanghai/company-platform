package com.company.platform.tool.excel.model;

import java.util.Objects;
import java.util.function.Function;

public record ExcelColumn<T>(String header, Function<T, ?> value, ExcelCellStyle style, Integer widthCharacters,
                             boolean hidden) {
    public ExcelColumn {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("header is required");
        Objects.requireNonNull(value, "value");
        style = style == null ? ExcelCellStyle.DEFAULT : style;
        if (widthCharacters != null && (widthCharacters < 1 || widthCharacters > 255))
            throw new IllegalArgumentException("widthCharacters must be 1..255");
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private String header;
        private Function<T, ?> value;
        private ExcelCellStyle style = ExcelCellStyle.DEFAULT;
        private Integer width;
        private boolean hidden;

        public Builder<T> header(String value) {
            header = value;
            return this;
        }

        public Builder<T> value(Function<T, ?> function) {
            value = function;
            return this;
        }

        public Builder<T> style(ExcelCellStyle value) {
            style = value;
            return this;
        }

        public Builder<T> widthCharacters(int value) {
            width = value;
            return this;
        }

        public Builder<T> hidden(boolean value) {
            hidden = value;
            return this;
        }

        public ExcelColumn<T> build() {
            return new ExcelColumn<>(header, value, style, width, hidden);
        }
    }
}
