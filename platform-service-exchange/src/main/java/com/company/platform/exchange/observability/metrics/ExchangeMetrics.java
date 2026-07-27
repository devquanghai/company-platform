package com.company.platform.exchange.observability.metrics;

import com.company.platform.exchange.domain.model.ExchangeProtocol;

import java.time.Duration;

public interface ExchangeMetrics {
    void record(
        String client, ExchangeProtocol protocol, String method,
        String outcome, String statusGroup, String exceptionCategory,
        boolean fallback, Duration duration, int retries);
}
