package com.company.platform.tool.csv.internal;

import com.company.platform.tool.api.UnsafeFileException;
import com.company.platform.tool.common.NonClosingOutputStream;
import com.company.platform.tool.csv.api.CsvExportService;
import com.company.platform.tool.csv.api.CsvImportService;
import com.company.platform.tool.csv.model.CsvColumn;
import com.company.platform.tool.csv.model.CsvExportRequest;
import com.company.platform.tool.csv.model.CsvImportRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public final class CommonsCsvService implements CsvExportService, CsvImportService {
    @Override
    public <T> void export(CsvExportRequest<T> request, OutputStream output) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(request.delimiter()).setHeader(request.columns().stream().map(CsvColumn::header).toArray(String[]::new)).get();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new NonClosingOutputStream(output), request.charset())); CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (T row : request.rows()) {
                List<String> values = new ArrayList<>(request.columns().size());
                for (CsvColumn<T> column : request.columns()) values.add(safe(column.value().apply(row)));
                printer.printRecord(values);
            }
        }
    }

    @Override
    public List<Map<String, String>> importFile(InputStream input, CsvImportRequest request) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(request.delimiter()).setHeader().setSkipHeaderRecord(true).get();
        List<Map<String, String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, request.charset())); CSVParser parser = format.parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            if (!headers.containsAll(request.requiredHeaders()))
                throw new UnsafeFileException("CSV is missing required headers");
            for (CSVRecord record : parser) {
                if (result.size() >= request.maximumRows()) throw new UnsafeFileException("CSV row limit exceeded");
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) row.put(header, record.get(header));
                result.add(Map.copyOf(row));
            }
        }
        return List.copyOf(result);
    }

    private static String safe(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) return "'" + text;
        return text;
    }
}
