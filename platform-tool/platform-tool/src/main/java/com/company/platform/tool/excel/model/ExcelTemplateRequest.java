package com.company.platform.tool.excel.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExcelTemplateRequest(InputStreamSource template, Map<String, ?> parameters, Map<String, ?> cellValues,
                                   Map<String, ?> namedRangeValues, List<RowInsertion> rowInsertions,
                                   long maximumTemplateBytes) {
    public ExcelTemplateRequest {
        Objects.requireNonNull(template, "template");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        cellValues = cellValues == null ? Map.of() : Map.copyOf(cellValues);
        namedRangeValues = namedRangeValues == null ? Map.of() : Map.copyOf(namedRangeValues);
        rowInsertions = rowInsertions == null ? List.of() : List.copyOf(rowInsertions);
        maximumTemplateBytes = maximumTemplateBytes < 1 ? 20L * 1024 * 1024 : maximumTemplateBytes;
    }

    @FunctionalInterface
    public interface InputStreamSource {
        InputStream open() throws IOException;
    }

    public record RowInsertion(String sheetName, int startRow, List<List<?>> rows) {
        public RowInsertion {
            if (sheetName == null || sheetName.isBlank() || startRow < 0)
                throw new IllegalArgumentException("Invalid row insertion");
            rows = rows.stream().<List<?>>map(List::copyOf).toList();
        }
    }
}
