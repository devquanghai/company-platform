package com.company.platform.core.context;

public interface RequestContextProvider {

    String getRequestId();

    String getCorrelationId();

    default String getRequestUrl() {
        return null;
    }

    default String getRequestMethod() {
        return null;
    }

    default String getRemoteAddress() {
        return null;
    }

    default String getUserAgent() {
        return null;
    }
}
