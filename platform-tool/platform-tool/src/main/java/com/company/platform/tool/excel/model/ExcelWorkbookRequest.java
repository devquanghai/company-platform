package com.company.platform.tool.excel.model;

import java.util.ArrayList;
import java.util.List;

public record ExcelWorkbookRequest(List<ExcelSheetRequest<?>> sheets) {
    public ExcelWorkbookRequest {
        sheets = List.copyOf(sheets);
        if (sheets.isEmpty()) throw new IllegalArgumentException("At least one sheet is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ExcelSheetRequest<?>> sheets = new ArrayList<>();

        public Builder sheet(ExcelSheetRequest<?> sheet) {
            sheets.add(sheet);
            return this;
        }

        public ExcelWorkbookRequest build() {
            return new ExcelWorkbookRequest(sheets);
        }
    }
}
