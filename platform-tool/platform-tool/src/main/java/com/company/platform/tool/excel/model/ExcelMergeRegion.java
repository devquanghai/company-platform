package com.company.platform.tool.excel.model;

public record ExcelMergeRegion(int firstRow, int lastRow, int firstColumn, int lastColumn) {
    public ExcelMergeRegion { if (firstRow < 0 || firstColumn < 0 || lastRow < firstRow || lastColumn < firstColumn) throw new IllegalArgumentException("Invalid merge range"); if (firstRow == lastRow && firstColumn == lastColumn) throw new IllegalArgumentException("Merge must contain at least two cells"); }
    public boolean overlaps(ExcelMergeRegion other) { return firstRow <= other.lastRow && lastRow >= other.firstRow && firstColumn <= other.lastColumn && lastColumn >= other.firstColumn; }
}
