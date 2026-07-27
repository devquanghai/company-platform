package com.company.platform.core.web.wrapper;

import java.io.IOException;

/** Signals that a request body cannot be safely retained within the configured limit. */
public final class RequestBodyCachingLimitExceededException extends IOException {

    private final int maximumBodySize;

    public RequestBodyCachingLimitExceededException(int maximumBodySize) {
        super("Request body exceeds the configured caching limit");
        this.maximumBodySize = maximumBodySize;
    }

    public int getMaximumBodySize() {
        return maximumBodySize;
    }
}
