package com.company.platform.tool.file.api;

import com.company.platform.tool.file.model.FileInspection;

import java.io.IOException;
import java.io.InputStream;

public interface FileInspectionService {
    FileInspection inspect(InputStream input, String claimedFilename, long maximumBytes) throws IOException;
}
