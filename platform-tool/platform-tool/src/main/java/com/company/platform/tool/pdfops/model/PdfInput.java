package com.company.platform.tool.pdfops.model;

import java.io.IOException;
import java.io.InputStream;

public record PdfInput(InputStreamSource source, long maximumBytes) { public PdfInput { if (source == null) throw new IllegalArgumentException("source is required"); maximumBytes = maximumBytes < 1 ? 50L * 1024 * 1024 : maximumBytes; } @FunctionalInterface public interface InputStreamSource { InputStream open() throws IOException; } }
