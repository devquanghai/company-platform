package com.company.platform.exchange.observability.metrics;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.List;

public final class MicrometerExchangeMetrics implements ExchangeMetrics {

    private final MeterRegistry registry;

    public MicrometerExchangeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(
        String client, ExchangeProtocol protocol, String method,
        String outcome, String statusGroup, String exceptionCategory,
        boolean fallback, Duration duration, int retries
    ) {
        List<Tag> tags = List.of(
            Tag.of("client", client),
            Tag.of("protocol", protocol.name()),
            Tag.of("method", method),
            Tag.of("outcome", outcome),
            Tag.of("status_group", statusGroup),
            Tag.of("exception_category", exceptionCategory),
            Tag.of("fallback", Boolean.toString(fallback)));
        registry.counter("platform.exchange.calls", tags).increment();
        Timer.builder("platform.exchange.call.duration").tags(tags)
            .register(registry).record(duration);
        if (retries > 0) {
            registry.counter("platform.exchange.retries", tags).increment(retries);
        }
        if (fallback) {
            registry.counter("platform.exchange.fallbacks", tags).increment();
        }
        if (!"SUCCESS".equals(outcome)) {
            registry.counter("platform.exchange.errors", tags).increment();
        }
    }
}
