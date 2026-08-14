package com.company.platform.exchange.api.exception;

public final class ServiceExchangeClientException extends RuntimeException {
    private final String clientName;
    private final String method;
    private final Integer status;
    private final boolean retryable;
    private final boolean recordable;

    public ServiceExchangeClientException(
        String clientName,
        String method,
        Integer status,
        boolean retryable,
        Throwable cause
    ) {
        this(clientName, method, status, retryable, retryable, cause);
    }

    public ServiceExchangeClientException(
        String clientName,
        String method,
        Integer status,
        boolean retryable,
        boolean recordable,
        Throwable cause
    ) {
        super("Service exchange call failed: client=" + clientName
            + ", method=" + method + ", status=" + status, cause);
        this.clientName = clientName;
        this.method = method;
        this.status = status;
        this.retryable = retryable;
        this.recordable = recordable;
    }

    public String clientName() { return clientName; }
    public String method() { return method; }
    public Integer status() { return status; }
    public boolean retryable() { return retryable; }
    public boolean recordable() { return recordable; }
}
