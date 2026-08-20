package com.company.platform.tool.csv.api;

import com.company.platform.tool.csv.model.CsvExportRequest;

import java.io.IOException;
import java.io.OutputStream;

public interface CsvExportService {
    <T> void export(CsvExportRequest<T> request, OutputStream output) throws IOException;
}
