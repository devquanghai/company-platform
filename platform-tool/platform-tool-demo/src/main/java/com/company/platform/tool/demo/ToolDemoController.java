package com.company.platform.tool.demo;

import com.company.platform.tool.archive.api.ArchiveService;
import com.company.platform.tool.archive.model.ArchiveEntrySource;
import com.company.platform.tool.csv.api.CsvExportService;
import com.company.platform.tool.csv.api.CsvImportService;
import com.company.platform.tool.csv.model.CsvColumn;
import com.company.platform.tool.csv.model.CsvExportRequest;
import com.company.platform.tool.csv.model.CsvImportRequest;
import com.company.platform.tool.excel.api.ExcelExportService;
import com.company.platform.tool.excel.api.ExcelImportService;
import com.company.platform.tool.excel.api.ExcelTemplateService;
import com.company.platform.tool.excel.model.ExcelAlignment;
import com.company.platform.tool.excel.model.ExcelCellStyle;
import com.company.platform.tool.excel.model.ExcelColumn;
import com.company.platform.tool.excel.model.ExcelImportRequest;
import com.company.platform.tool.excel.model.ExcelImportResult;
import com.company.platform.tool.excel.model.ExcelSheetRequest;
import com.company.platform.tool.excel.model.ExcelTemplateRequest;
import com.company.platform.tool.excel.model.ExcelWorkbookRequest;
import com.company.platform.tool.excel.model.TrustedFormula;
import com.company.platform.tool.pdf.api.PdfExportService;
import com.company.platform.tool.pdf.model.PdfExportRequest;
import com.company.platform.tool.pdf.model.PdfFont;
import com.company.platform.tool.pdfops.api.PdfOperationService;
import com.company.platform.tool.pdfops.model.PdfInput;
import com.company.platform.tool.file.api.DigestService;
import com.company.platform.tool.file.api.FileInspectionService;
import com.company.platform.tool.file.model.DigestAlgorithm;
import com.company.platform.tool.file.model.FileInspection;
import com.company.platform.tool.qrcode.api.QrCodeService;
import com.company.platform.tool.qrcode.model.QrCodeRequest;
import com.company.platform.tool.template.api.TemplateRenderer;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/tools")
public class ToolDemoController {
    private final ExcelExportService excelExport;
    private final ExcelImportService excelImport;
    private final ExcelTemplateService excelTemplate;
    private final TemplateRenderer templates;
    private final PdfExportService pdfExport;
    private final PdfOperationService pdfOperations;
    private final CsvExportService csvExport;
    private final CsvImportService csvImport;
    private final ArchiveService archives;
    private final QrCodeService qrCodes;
    private final FileInspectionService fileInspection;
    private final DigestService digests;

    public ToolDemoController(ExcelExportService excelExport, ExcelImportService excelImport, ExcelTemplateService excelTemplate, TemplateRenderer templates, PdfExportService pdfExport, PdfOperationService pdfOperations, CsvExportService csvExport, CsvImportService csvImport, ArchiveService archives, QrCodeService qrCodes, FileInspectionService fileInspection, DigestService digests) {
        this.excelExport = excelExport;
        this.excelImport = excelImport;
        this.excelTemplate = excelTemplate;
        this.templates = templates;
        this.pdfExport = pdfExport;
        this.pdfOperations = pdfOperations;
        this.csvExport = csvExport;
        this.csvImport = csvImport;
        this.archives = archives;
        this.qrCodes = qrCodes;
        this.fileInspection = fileInspection;
        this.digests = digests;
    }

    @PostMapping("/excel/export")
    public ResponseEntity<StreamingResponseBody> exportExcel() {
        return download("customer-report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output -> excelExport.export(workbook(), output));
    }

    @PostMapping("/excel/export/template")
    public ResponseEntity<StreamingResponseBody> exportExcelTemplate() {
        List<List<?>> rows = customers().stream().map(row -> List.of(row.id(), row.customerCode(), row.customerName(), row.amount(), row.interestRate(), row.status())).<List<?>>map(values -> values).toList();
        ExcelTemplateRequest request = new ExcelTemplateRequest(() -> new ClassPathResource("templates/excel/customer-report.xlsx").getInputStream(), Map.of("reportTitle", "Báo cáo khách hàng", "generatedAt", Instant.now().toString(), "customerName", "Nguyễn Văn An"), Map.of(), Map.of("CUSTOMER_NAME", "Nguyễn Văn An"), List.of(new ExcelTemplateRequest.RowInsertion("Customers", 4, rows)), 10 * 1024 * 1024L);
        return download("customer-report-template.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output -> excelTemplate.export(request, output));
    }

    @PostMapping(value = "/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelImportResult<Map<String, String>> importExcel(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        ExcelImportRequest<Map<String, String>> request = new ExcelImportRequest<>(file.getOriginalFilename(), "Customers", null, 1, 2, true, false, List.of("Customer Code", "Customer Name"), 20, 100_000, 128, 20L * 1024 * 1024, row -> row.values());
        return excelImport.importFile(file.getInputStream(), request);
    }

    @PostMapping(value = "/template/render", produces = MediaType.TEXT_HTML_VALUE)
    public String renderTemplate(@RequestBody(required = false) Map<String, Object> parameters) {
        return templates.render("customer-report", reportParameters(parameters));
    }

    @PostMapping("/pdf/export")
    public ResponseEntity<StreamingResponseBody> exportPdf(@RequestBody(required = false) Map<String, Object> parameters) {
        String html = templates.render("customer-report", reportParameters(parameters));
        PdfFont font = new PdfFont("Droid Sans", () -> new ClassPathResource("fonts/DroidSans.ttf").getInputStream());
        PdfExportRequest request = new PdfExportRequest(html, 210, 297, null, 12, "CONFIDENTIAL", "Customer report", "Platform Tool Demo", List.of(font), Map.of(), 2_000_000);
        return download("customer-report.pdf", "application/pdf", output -> pdfExport.export(request, output));
    }

    @PostMapping(value = "/pdf/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> mergePdf(@RequestPart("files") List<MultipartFile> files) {
        List<PdfInput> inputs = files.stream().map(file -> new PdfInput(file::getInputStream, 20 * 1024 * 1024L)).toList();
        return download("merged.pdf", "application/pdf", output -> pdfOperations.merge(inputs, output));
    }

    @PostMapping("/csv/export")
    public ResponseEntity<StreamingResponseBody> exportCsv() {
        List<CsvColumn<CustomerReportRow>> columns = List.of(new CsvColumn<>("customerCode", CustomerReportRow::customerCode), new CsvColumn<>("customerName", CustomerReportRow::customerName), new CsvColumn<>("amount", CustomerReportRow::amount), new CsvColumn<>("status", CustomerReportRow::status));
        return download("customers.csv", "text/csv;charset=UTF-8", output -> csvExport.export(CsvExportRequest.utf8(customers(), columns), output));
    }

    @PostMapping(value = "/csv/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Map<String, String>> importCsv(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        return csvImport.importFile(file.getInputStream(), CsvImportRequest.secureDefaults());
    }

    @PostMapping("/archive/zip")
    public ResponseEntity<StreamingResponseBody> zip(@RequestBody Map<String, String> files) {
        List<ArchiveEntrySource> entries = files.entrySet().stream().map(entry -> new ArchiveEntrySource(entry.getKey(), () -> new ByteArrayInputStream(entry.getValue().getBytes(StandardCharsets.UTF_8)))).toList();
        return download("reports.zip", "application/zip", output -> archives.createZip(entries, output));
    }

    @PostMapping("/qrcode")
    public ResponseEntity<StreamingResponseBody> qr(@RequestParam String content) {
        return download("qrcode.png", MediaType.IMAGE_PNG_VALUE, output -> qrCodes.writePng(QrCodeRequest.png(content, 320), output));
    }

    @PostMapping(value = "/file/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileInspection inspect(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        return fileInspection.inspect(file.getInputStream(), file.getOriginalFilename(), 20 * 1024 * 1024L);
    }

    @PostMapping(value = "/file/digest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> digest(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "SHA_256") DigestAlgorithm algorithm) throws java.io.IOException {
        return Map.of("algorithm", algorithm.name(), "digest", digests.digest(file.getInputStream(), algorithm));
    }

    private ExcelWorkbookRequest workbook() {
        ExcelCellStyle currency = new ExcelCellStyle("#,##0.00 [$₫-vi-VN]", ExcelAlignment.RIGHT, null, false, true, false);
        ExcelCellStyle percentage = new ExcelCellStyle("0.00%", ExcelAlignment.RIGHT, null, false, true, false);
        ExcelCellStyle date = new ExcelCellStyle("dd/mm/yyyy hh:mm", ExcelAlignment.CENTER, null, false, true, false);
        List<ExcelColumn<CustomerReportRow>> columns = List.of(ExcelColumn.<CustomerReportRow>builder().header("ID").value(CustomerReportRow::id).widthCharacters(10).build(), ExcelColumn.<CustomerReportRow>builder().header("Customer Code").value(CustomerReportRow::customerCode).widthCharacters(18).build(), ExcelColumn.<CustomerReportRow>builder().header("Customer Name").value(CustomerReportRow::customerName).widthCharacters(28).build(), ExcelColumn.<CustomerReportRow>builder().header("Amount").value(CustomerReportRow::amount).style(currency).build(), ExcelColumn.<CustomerReportRow>builder().header("Interest Rate").value(CustomerReportRow::interestRate).style(percentage).build(), ExcelColumn.<CustomerReportRow>builder().header("Created At").value(CustomerReportRow::createdAt).style(date).widthCharacters(20).build(), ExcelColumn.<CustomerReportRow>builder().header("Status").value(CustomerReportRow::status).build());
        ExcelSheetRequest<CustomerReportRow> customerSheet = ExcelSheetRequest.<CustomerReportRow>builder().name("Customers").title("CUSTOMER REPORT").rows(customers()).columns(columns).freezeHeader(true).autoFilter(true).streaming(true).build();
        record Summary(String metric, TrustedFormula value) {
        }
        List<Summary> summaries = List.of(new Summary("TOTAL", TrustedFormula.of("SUM(Customers!D3:D7)")), new Summary("AVERAGE", TrustedFormula.of("AVERAGE(Customers!D3:D7)")), new Summary("MIN", TrustedFormula.of("MIN(Customers!D3:D7)")), new Summary("MAX", TrustedFormula.of("MAX(Customers!D3:D7)")), new Summary("COUNT", TrustedFormula.of("COUNT(Customers!D3:D7)")));
        ExcelSheetRequest<Summary> summarySheet = ExcelSheetRequest.<Summary>builder().name("Summary").title("SUMMARY").rows(summaries).columns(List.of(ExcelColumn.<Summary>builder().header("Metric").value(Summary::metric).build(), ExcelColumn.<Summary>builder().header("Value").value(Summary::value).style(currency).build())).build();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generatedAt", LocalDateTime.now());
        metadata.put("recordCount", customers().size());
        metadata.put("application", "platform-tool-demo");
        ExcelSheetRequest<Map.Entry<String, Object>> metadataSheet = ExcelSheetRequest.<Map.Entry<String, Object>>builder().name("Metadata").rows(metadata.entrySet()).columns(List.of(ExcelColumn.<Map.Entry<String, Object>>builder().header("Key").value(Map.Entry::getKey).build(), ExcelColumn.<Map.Entry<String, Object>>builder().header("Value").value(Map.Entry::getValue).build())).build();
        return ExcelWorkbookRequest.builder().sheet(customerSheet).sheet(summarySheet).sheet(metadataSheet).build();
    }

    private static List<CustomerReportRow> customers() {
        return List.of(new CustomerReportRow(1, "CUST-001", "Nguyễn Văn An", new BigDecimal("125000000.50"), new BigDecimal("0.0725"), LocalDateTime.now().minusDays(5), "ACTIVE"), new CustomerReportRow(2, "CUST-002", "Trần Thị Bình", new BigDecimal("85000000"), new BigDecimal("0.068"), LocalDateTime.now().minusDays(4), "ACTIVE"), new CustomerReportRow(3, "CUST-003", "Lê Minh Châu", new BigDecimal("300000000"), new BigDecimal("0.081"), LocalDateTime.now().minusDays(3), "REVIEW"), new CustomerReportRow(4, "CUST-004", "Phạm Quốc Dũng", new BigDecimal("45000000"), new BigDecimal("0.065"), LocalDateTime.now().minusDays(2), "ACTIVE"), new CustomerReportRow(5, "CUST-005", "Hoàng Thu Hà", new BigDecimal("210000000"), new BigDecimal("0.075"), LocalDateTime.now().minusDays(1), "INACTIVE"));
    }

    private static Map<String, Object> reportParameters(Map<String, Object> supplied) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reportTitle", "Báo cáo khách hàng");
        params.put("customerName", "Nguyễn Văn An");
        params.put("generatedAt", Instant.now().toString());
        params.put("transactions", customers());
        params.put("totalAmount", "765.000.000,50 ₫");
        if (supplied != null) params.putAll(supplied);
        return params;
    }

    private static ResponseEntity<StreamingResponseBody> download(String filename, String contentType, StreamingResponseBody body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
