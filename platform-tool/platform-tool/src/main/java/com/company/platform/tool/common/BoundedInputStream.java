package com.company.platform.tool.common;

import com.company.platform.tool.api.UnsafeFileException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BoundedInputStream extends FilterInputStream {
    private final long maximumBytes;
    private long count;

    public BoundedInputStream(InputStream input, long maximumBytes) {
        super(input);
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    @Override public int read() throws IOException { int value = super.read(); if (value >= 0) add(1); return value; }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        int read = super.read(bytes, offset, length); if (read > 0) add(read); return read;
    }
    private void add(long value) { count += value; if (count > maximumBytes) throw new UnsafeFileException("Input exceeds the permitted size"); }
}
