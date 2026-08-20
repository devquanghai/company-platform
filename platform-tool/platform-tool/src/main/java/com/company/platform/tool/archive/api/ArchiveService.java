package com.company.platform.tool.archive.api;

import com.company.platform.tool.archive.model.ArchiveEntrySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

public interface ArchiveService {
    void createZip(List<ArchiveEntrySource> entries, OutputStream output) throws IOException;

    List<Path> extractZip(InputStream input, Path destination, long maximumExpandedBytes, int maximumEntries) throws IOException;
}
