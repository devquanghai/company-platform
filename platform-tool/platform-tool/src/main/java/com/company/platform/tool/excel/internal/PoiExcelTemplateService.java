package com.company.platform.tool.excel.internal;

import com.company.platform.tool.common.BoundedInputStream;
import com.company.platform.tool.excel.api.ExcelTemplateService;
import com.company.platform.tool.excel.api.InvalidExcelTemplateException;
import com.company.platform.tool.excel.model.ExcelTemplateRequest;
import com.company.platform.tool.excel.model.TrustedFormula;
import com.samskivert.mustache.Mustache;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;

public final class PoiExcelTemplateService implements ExcelTemplateService {
    private final Mustache.Compiler compiler;
    public PoiExcelTemplateService(Mustache.Compiler compiler) { this.compiler = compiler.escapeHTML(false); }
    @Override public void export(ExcelTemplateRequest request, OutputStream output) {
        try (InputStream source = request.template().open(); BufferedInputStream input = new BufferedInputStream(new BoundedInputStream(source, request.maximumTemplateBytes())); Workbook workbook = WorkbookFactory.create(input)) {
            replacePlaceholders(workbook, request.parameters());
            request.cellValues().forEach((reference, value) -> setReference(workbook, reference, value));
            request.namedRangeValues().forEach((name, value) -> setNamedRange(workbook, name, value));
            for (ExcelTemplateRequest.RowInsertion insertion : request.rowInsertions()) insertRows(workbook, insertion);
            workbook.write(output);
        } catch (IOException | RuntimeException exception) { if (exception instanceof InvalidExcelTemplateException invalid) throw invalid; throw new InvalidExcelTemplateException("Unable to process Excel template", exception); }
    }
    private void replacePlaceholders(Workbook workbook, Map<String, ?> parameters) {
        for (Sheet sheet : workbook) for (Row row : sheet) for (Cell cell : row) if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains("{{")) cell.setCellValue(compiler.compile(cell.getStringCellValue()).execute(parameters));
    }
    private static void setReference(Workbook workbook, String reference, Object value) {
        int bang = reference == null ? -1 : reference.indexOf('!'); if (bang < 1) throw new InvalidExcelTemplateException("Cell reference must use Sheet!A1");
        Sheet sheet = workbook.getSheet(reference.substring(0, bang)); if (sheet == null) throw new InvalidExcelTemplateException("Unknown template sheet");
        CellReference cellReference = new CellReference(reference.substring(bang + 1)); Row row = sheet.getRow(cellReference.getRow()); if (row == null) row = sheet.createRow(cellReference.getRow()); Cell cell = row.getCell(cellReference.getCol()); if (cell == null) cell = row.createCell(cellReference.getCol()); setValue(cell, value);
    }
    private static void setNamedRange(Workbook workbook, String name, Object value) {
        Name defined = workbook.getName(name); if (defined == null || defined.getRefersToFormula() == null) throw new InvalidExcelTemplateException("Unknown named range");
        AreaReference area = new AreaReference(defined.getRefersToFormula(), workbook.getSpreadsheetVersion()); CellReference first = area.getFirstCell(); String sheetName = first.getSheetName(); if (sheetName == null) throw new InvalidExcelTemplateException("Named range must identify a sheet");
        setReference(workbook, sheetName + "!" + first.formatAsString().replace("'" + sheetName + "'!", "").replace(sheetName + "!", ""), value);
    }
    private static void insertRows(Workbook workbook, ExcelTemplateRequest.RowInsertion insertion) {
        Sheet sheet = workbook.getSheet(insertion.sheetName()); if (sheet == null) throw new InvalidExcelTemplateException("Unknown insertion sheet"); int count = insertion.rows().size(); if (count == 0) return;
        if (sheet.getLastRowNum() >= insertion.startRow()) sheet.shiftRows(insertion.startRow(), sheet.getLastRowNum(), count, true, false);
        Row styleSource = insertion.startRow() + count <= sheet.getLastRowNum() ? sheet.getRow(insertion.startRow() + count) : null;
        for (int offset = 0; offset < count; offset++) { Row row = sheet.createRow(insertion.startRow() + offset); List<?> values = insertion.rows().get(offset); for (int column = 0; column < values.size(); column++) { Cell cell = row.createCell(column); if (styleSource != null && styleSource.getCell(column) != null) cell.setCellStyle(styleSource.getCell(column).getCellStyle()); setValue(cell, values.get(column)); } }
    }
    private static void setValue(Cell cell, Object value) {
        if (value == null) { cell.setBlank(); return; } if (value instanceof TrustedFormula formula) { cell.setCellFormula(formula.expression()); return; } if (value instanceof Number number) { cell.setCellValue(number.doubleValue()); return; } if (value instanceof Boolean bool) { cell.setCellValue(bool); return; } if (value instanceof java.time.LocalDate date) { cell.setCellValue(date); return; } if (value instanceof java.time.LocalDateTime dateTime) { cell.setCellValue(dateTime); return; } cell.setCellValue(String.valueOf(value));
    }
}
