package com.company.platform.tool.excel.internal;

import com.company.platform.tool.api.UnsafeFileException;
import com.company.platform.tool.common.BoundedInputStream;
import com.company.platform.tool.common.ToolObservations;
import com.company.platform.tool.excel.api.ExcelImportException;
import com.company.platform.tool.excel.api.ExcelImportService;
import com.company.platform.tool.excel.model.ExcelImportError;
import com.company.platform.tool.excel.model.ExcelImportRequest;
import com.company.platform.tool.excel.model.ExcelImportResult;
import com.company.platform.tool.excel.model.ExcelRowData;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import io.micrometer.observation.ObservationRegistry;

public final class PoiExcelImportService implements ExcelImportService {
    private final ObservationRegistry observations;
    public PoiExcelImportService(ObservationRegistry observations) { this.observations = observations; }
    @Override public <T> ExcelImportResult<T> importFile(InputStream input, ExcelImportRequest<T> request) {
        return ToolObservations.observe("platform.tool.excel.import", "workbook", observations, () -> importInternal(input, request));
    }
    private <T> ExcelImportResult<T> importInternal(InputStream input, ExcelImportRequest<T> request) {
        validateExtension(request.claimedFilename());
        try (BufferedInputStream bounded = new BufferedInputStream(new BoundedInputStream(input, request.maximumBytes()))) {
            validateMagic(bounded);
            try (Workbook workbook = WorkbookFactory.create(bounded)) {
                if (workbook.getNumberOfSheets() > request.maximumSheets()) throw new UnsafeFileException("Excel sheet limit exceeded");
                Sheet sheet = selectSheet(workbook, request); return readSheet(sheet, request);
            }
        } catch (UnsafeFileException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw new ExcelImportException("Unable to import Excel workbook", exception); }
    }
    private static <T> ExcelImportResult<T> readSheet(Sheet sheet, ExcelImportRequest<T> request) {
        Row headerRow = sheet.getRow(request.headerRow()); if (headerRow == null) throw new UnsafeFileException("Excel header row is missing");
        int columnCount = headerRow.getLastCellNum(); if (columnCount < 1 || columnCount > request.maximumColumns()) throw new UnsafeFileException("Invalid Excel column count");
        DataFormatter formatter = new DataFormatter(Locale.ROOT); List<String> headers = new ArrayList<>(columnCount);
        for (int column = 0; column < columnCount; column++) { String header = formatter.formatCellValue(headerRow.getCell(column)).trim(); if (header.isEmpty() || headers.contains(header)) throw new UnsafeFileException("Excel headers must be non-empty and unique"); headers.add(header); }
        if (!headers.containsAll(request.requiredColumns())) throw new UnsafeFileException("Excel is missing required columns");
        List<T> records = new ArrayList<>(); List<ExcelImportError> errors = new ArrayList<>(); long total = 0; long failed = 0;
        int last = Math.min(sheet.getLastRowNum(), request.startRow() + request.maximumRows() - 1);
        for (int rowIndex = request.startRow(); rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex); if (row == null || (request.skipBlankRows() && blank(row, columnCount, formatter))) continue; total++;
            Map<String, String> values = new LinkedHashMap<>(); for (int column = 0; column < columnCount; column++) values.put(headers.get(column), untrustedValue(row.getCell(column), formatter));
            try { T mapped = request.mapper().map(new ExcelRowData(rowIndex + 1L, values)); if (mapped != null) records.add(mapped); else throw new IllegalArgumentException("Row mapper returned null"); }
            catch (Exception exception) { failed++; errors.add(new ExcelImportError(rowIndex + 1L, "", "ROW_INVALID", safeMessage(exception))); if (request.failFast()) break; }
        }
        if (sheet.getLastRowNum() >= request.startRow() + request.maximumRows()) throw new UnsafeFileException("Excel row limit exceeded");
        return new ExcelImportResult<>(records, errors, total, records.size(), failed);
    }
    private static String untrustedValue(Cell cell, DataFormatter formatter) { if (cell == null) return ""; if (cell.getCellType() == CellType.FORMULA) return "=" + cell.getCellFormula(); return formatter.formatCellValue(cell); }
    private static boolean blank(Row row, int columns, DataFormatter formatter) { for (int i = 0; i < columns; i++) if (!formatter.formatCellValue(row.getCell(i)).isBlank()) return false; return true; }
    private static String safeMessage(Exception exception) { String message = exception.getMessage(); return message == null || message.length() > 256 ? "Row validation failed" : message.replaceAll("[\r\n\t]", " "); }
    private static <T> Sheet selectSheet(Workbook workbook, ExcelImportRequest<T> request) { if (request.sheetName() != null) { Sheet sheet = workbook.getSheet(request.sheetName()); if (sheet == null) throw new UnsafeFileException("Requested Excel sheet does not exist"); return sheet; } int index = request.sheetIndex() == null ? 0 : request.sheetIndex(); if (index < 0 || index >= workbook.getNumberOfSheets()) throw new UnsafeFileException("Requested Excel sheet index does not exist"); return workbook.getSheetAt(index); }
    private static void validateExtension(String name) { String lower = name == null ? "" : name.toLowerCase(Locale.ROOT); if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls")) || lower.endsWith(".xlsm") || lower.endsWith(".xlam") || lower.endsWith(".xltm")) throw new UnsafeFileException("Only .xlsx and .xls workbooks are accepted"); }
    private static void validateMagic(BufferedInputStream input) throws IOException { input.mark(8); byte[] magic = input.readNBytes(8); input.reset(); boolean zip = magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4; boolean ole = magic.length == 8 && (magic[0] & 255) == 0xD0 && (magic[1] & 255) == 0xCF && (magic[2] & 255) == 0x11 && (magic[3] & 255) == 0xE0; if (!zip && !ole) throw new UnsafeFileException("File signature is not an Excel workbook"); }
}
