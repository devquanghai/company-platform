package com.company.platform.tool.excel.model;

import java.util.Map;

public record ExcelRowData(long rowNumber, Map<String, String> values) {
    public ExcelRowData {
        values = Map.copyOf(values);
    }
}
