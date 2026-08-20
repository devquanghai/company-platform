package com.company.platform.tool.excel.internal;

import com.company.platform.tool.excel.api.ExcelExportException;
import com.company.platform.tool.excel.api.ExcelExportService;
import com.company.platform.tool.common.ToolObservations;
import com.company.platform.tool.excel.model.ExcelAlignment;
import com.company.platform.tool.excel.model.ExcelCellStyle;
import com.company.platform.tool.excel.model.ExcelColumn;
import com.company.platform.tool.excel.model.ExcelMergeRegion;
import com.company.platform.tool.excel.model.ExcelSheetRequest;
import com.company.platform.tool.excel.model.ExcelWorkbookRequest;
import com.company.platform.tool.excel.model.TrustedFormula;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.micrometer.observation.ObservationRegistry;

public final class PoiExcelExportService implements ExcelExportService {
    private static final int MAX_WIDTH = 80;
    private final ObservationRegistry observations;

    public PoiExcelExportService(ObservationRegistry observations) {
        this.observations = observations;
    }

    @Override
    public void export(ExcelWorkbookRequest request, OutputStream output) {
        ToolObservations.observe("platform.tool.excel.export", request.sheets().stream().anyMatch(ExcelSheetRequest::streaming) ? "streaming" : "standard", observations, () -> exportInternal(request, output));
    }

    private void exportInternal(ExcelWorkbookRequest request, OutputStream output) {
        validate(request);
        boolean streaming = request.sheets().stream().anyMatch(ExcelSheetRequest::streaming);
        Workbook workbook = streaming ? new SXSSFWorkbook(200) : new XSSFWorkbook();
        if (workbook instanceof SXSSFWorkbook sxssf) {
            sxssf.setCompressTempFiles(true);
        }
        try (workbook) {
            Map<ExcelCellStyle, CellStyle> styles = new HashMap<>();
            for (ExcelSheetRequest<?> sheet : request.sheets()) writeSheet(workbook, sheet, styles);
            workbook.write(output);
        } catch (IOException | RuntimeException exception) {
            throw new ExcelExportException("Unable to export Excel workbook", exception);
        } finally {
            if (workbook instanceof SXSSFWorkbook sxssf) sxssf.dispose();
        }
    }

    private static <T> void writeSheet(Workbook workbook, ExcelSheetRequest<T> request, Map<ExcelCellStyle, CellStyle> styles) {
        Sheet sheet = workbook.createSheet(request.name());
        int rowIndex = 0;
        int headerRow;
        int[] widths = new int[request.columns().size()];
        if (request.title() != null && !request.title().isBlank()) {
            Row row = sheet.createRow(rowIndex++);
            Cell cell = row.createCell(0);
            cell.setCellValue(request.title());
            cell.setCellStyle(style(workbook, new ExcelCellStyle(null, ExcelAlignment.CENTER, "1F4E78", true, true, false), styles));
            if (request.columns().size() > 1)
                addMerge(sheet, new ExcelMergeRegion(0, 0, 0, request.columns().size() - 1));
        }
        headerRow = rowIndex;
        Row header = sheet.createRow(rowIndex++);
        ExcelCellStyle headerStyle = new ExcelCellStyle(null, ExcelAlignment.CENTER, "D9EAF7", true, true, true);
        for (int columnIndex = 0; columnIndex < request.columns().size(); columnIndex++) {
            ExcelColumn<T> column = request.columns().get(columnIndex);
            Cell cell = header.createCell(columnIndex);
            cell.setCellValue(column.header());
            cell.setCellStyle(style(workbook, headerStyle, styles));
            widths[columnIndex] = column.header().length();
        }
        int count = 0;
        for (T item : request.rows()) {
            if (++count > request.maximumRows() || rowIndex > SpreadsheetVersion.EXCEL2007.getMaxRows() - 1)
                throw new IllegalArgumentException("Excel row limit exceeded");
            Row row = sheet.createRow(rowIndex++);
            for (int columnIndex = 0; columnIndex < request.columns().size(); columnIndex++) {
                ExcelColumn<T> column = request.columns().get(columnIndex);
                Object value = column.value().apply(item);
                Cell cell = row.createCell(columnIndex);
                writeValue(cell, value);
                cell.setCellStyle(style(workbook, column.style(), styles));
                widths[columnIndex] = Math.max(widths[columnIndex], displayLength(value));
            }
        }
        for (ExcelMergeRegion merge : request.merges()) addMerge(sheet, merge);
        for (int i = 0; i < request.columns().size(); i++) {
            ExcelColumn<T> column = request.columns().get(i);
            int chars = column.widthCharacters() == null ? Math.min(MAX_WIDTH, widths[i] + 2) : column.widthCharacters();
            sheet.setColumnWidth(i, Math.min(255 * 256, chars * 256));
            sheet.setColumnHidden(i, column.hidden());
        }
        if (request.freezeHeader()) sheet.createFreezePane(0, headerRow + 1);
        if (request.autoFilter())
            sheet.setAutoFilter(new CellRangeAddress(headerRow, Math.max(headerRow, rowIndex - 1), 0, request.columns().size() - 1));
    }

    private static void writeValue(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof TrustedFormula formula) {
            cell.setCellFormula(formula.expression());
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            return;
        }
        if (value instanceof Instant instant) {
            cell.setCellValue(Date.from(instant));
            return;
        }
        if (value instanceof Date date) {
            cell.setCellValue(date);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static int displayLength(Object value) {
        return value == null ? 0 : Math.min(MAX_WIDTH, String.valueOf(value).length());
    }

    private static CellStyle style(Workbook workbook, ExcelCellStyle spec, Map<ExcelCellStyle, CellStyle> cache) {
        return cache.computeIfAbsent(spec, ignored -> {
            CellStyle cellStyle = workbook.createCellStyle();
            if (spec.dataFormat() != null && !spec.dataFormat().isBlank())
                cellStyle.setDataFormat(workbook.createDataFormat().getFormat(spec.dataFormat()));
            cellStyle.setAlignment(switch (spec.alignment()) {
                case LEFT -> HorizontalAlignment.LEFT;
                case CENTER -> HorizontalAlignment.CENTER;
                case RIGHT -> HorizontalAlignment.RIGHT;
                default -> HorizontalAlignment.GENERAL;
            });
            cellStyle.setWrapText(spec.wrapText());
            if (spec.border()) {
                cellStyle.setBorderTop(BorderStyle.THIN);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBorderLeft(BorderStyle.THIN);
                cellStyle.setBorderRight(BorderStyle.THIN);
            }
            if (spec.bold()) {
                Font font = workbook.createFont();
                font.setBold(true);
                cellStyle.setFont(font);
            }
            if (spec.backgroundRgb() != null && cellStyle instanceof XSSFCellStyle xssf) {
                byte[] rgb = java.util.HexFormat.of().parseHex(spec.backgroundRgb());
                xssf.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
                xssf.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return cellStyle;
        });
    }

    private static void addMerge(Sheet sheet, ExcelMergeRegion merge) {
        CellRangeAddress range = new CellRangeAddress(merge.firstRow(), merge.lastRow(), merge.firstColumn(), merge.lastColumn());
        sheet.addMergedRegion(range);
        RegionUtil.setBorderTop(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, range, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, range, sheet);
    }

    private static void validate(ExcelWorkbookRequest request) {
        Set<String> names = new HashSet<>();
        for (ExcelSheetRequest<?> sheet : request.sheets()) {
            WorkbookUtil.validateSheetName(sheet.name());
            if (!names.add(sheet.name())) throw new IllegalArgumentException("Duplicate sheet name: " + sheet.name());
            if (sheet.columns().size() > SpreadsheetVersion.EXCEL2007.getMaxColumns())
                throw new IllegalArgumentException("Excel column limit exceeded");
            List<ExcelMergeRegion> merges = new ArrayList<>(sheet.merges());
            if (sheet.title() != null && !sheet.title().isBlank() && sheet.columns().size() > 1)
                merges.add(new ExcelMergeRegion(0, 0, 0, sheet.columns().size() - 1));
            for (int i = 0; i < merges.size(); i++) {
                ExcelMergeRegion left = merges.get(i);
                if (left.lastRow() >= SpreadsheetVersion.EXCEL2007.getMaxRows() || left.lastColumn() >= SpreadsheetVersion.EXCEL2007.getMaxColumns())
                    throw new IllegalArgumentException("Merge exceeds Excel limits");
                for (int j = i + 1; j < merges.size(); j++)
                    if (left.overlaps(merges.get(j))) throw new IllegalArgumentException("Overlapping merge regions");
            }
        }
    }
}
