package com.company.platform.core.web.internal.adapter.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Servlet request wrapper that provides repeatable, size-bounded body reads.
 */
public final class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequestWrapper(HttpServletRequest request)
        throws IOException {
        super(Objects.requireNonNull(request, "request must not be null"));
        byte[] content = request.getInputStream().readAllBytes();
        this.cachedBody = content;
    }

    public byte[] getCachedBody() {
        return cachedBody.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null
            ? StandardCharsets.UTF_8
            : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static final class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private CachedServletInputStream(byte[] content) {
            this.delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            Objects.requireNonNull(readListener, "readListener must not be null");
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
