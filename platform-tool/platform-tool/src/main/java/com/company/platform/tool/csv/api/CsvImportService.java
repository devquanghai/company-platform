package com.company.platform.tool.csv.api;

import com.company.platform.tool.csv.model.CsvImportRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface CsvImportService {
    List<Map<String, String>> importFile(InputStream input, CsvImportRequest request) throws IOException;
}
