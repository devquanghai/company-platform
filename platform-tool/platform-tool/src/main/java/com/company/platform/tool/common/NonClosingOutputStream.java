package com.company.platform.tool.common;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class NonClosingOutputStream extends FilterOutputStream {
    public NonClosingOutputStream(OutputStream output) {
        super(output);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
