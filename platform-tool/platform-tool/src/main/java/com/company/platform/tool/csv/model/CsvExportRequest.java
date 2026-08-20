package com.company.platform.tool.csv.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public record CsvExportRequest<T>(Iterable<T> rows, List<CsvColumn<T>> columns, char delimiter, Charset charset) {
    public CsvExportRequest {
        Objects.requireNonNull(rows, "rows");
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("columns are required");
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
    }

    public static <T> CsvExportRequest<T> utf8(Iterable<T> rows, List<CsvColumn<T>> columns) {
        return new CsvExportRequest<>(rows, columns, ',', StandardCharsets.UTF_8);
    }
}
