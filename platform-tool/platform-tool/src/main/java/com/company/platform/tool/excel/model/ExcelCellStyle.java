package com.company.platform.tool.excel.model;

public record ExcelCellStyle(String dataFormat, ExcelAlignment alignment, String backgroundRgb, boolean bold,
                             boolean border, boolean wrapText) {
    public static final ExcelCellStyle DEFAULT = new ExcelCellStyle(null, ExcelAlignment.GENERAL, null, false, false, false);

    public ExcelCellStyle {
        alignment = alignment == null ? ExcelAlignment.GENERAL : alignment;
        if (backgroundRgb != null && !backgroundRgb.matches("(?i)[0-9a-f]{6}"))
            throw new IllegalArgumentException("backgroundRgb must be six hexadecimal digits");
    }

    public ExcelCellStyle withFormat(String format) {
        return new ExcelCellStyle(format, alignment, backgroundRgb, bold, border, wrapText);
    }
}
