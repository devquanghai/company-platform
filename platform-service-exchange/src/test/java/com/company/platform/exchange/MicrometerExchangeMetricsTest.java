package com.company.platform.exchange;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.observability.metrics.MicrometerExchangeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerExchangeMetricsTest {

    @Test
    void recordsLowCardinalityCountersAndTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerExchangeMetrics metrics = new MicrometerExchangeMetrics(registry);

        metrics.record("client", ExchangeProtocol.HTTP, "GET", "FAILED",
            "5xx", "transport", true, Duration.ofMillis(12), 2);

        assertThat(registry.get("platform.exchange.calls").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.exchange.call.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("platform.exchange.retries").counter().count()).isEqualTo(2);
        assertThat(registry.get("platform.exchange.fallbacks").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.exchange.errors").counter().count()).isEqualTo(1);
    }

    @Test
    void successWithoutRetryDoesNotCreateOptionalCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new MicrometerExchangeMetrics(registry).record(
            "client", ExchangeProtocol.GRPC, "Call", "SUCCESS",
            "ok", "none", false, Duration.ZERO, 0);

        assertThat(registry.find("platform.exchange.errors").counter()).isNull();
        assertThat(registry.find("platform.exchange.retries").counter()).isNull();
        assertThat(registry.find("platform.exchange.fallbacks").counter()).isNull();
    }
}
