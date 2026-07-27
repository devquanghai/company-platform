package com.company.platform.exchange;

import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.OutboundCircuitOpenException;
import com.company.platform.exchange.domain.exception.OutboundHttpException;
import com.company.platform.exchange.domain.exception.OutboundRateLimitException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.resilience.executor.DefaultResilienceExecutor;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceExecutorTest {

    @Test
    void retriesRetryableFailureThenSucceeds() {
        ClientProperties client = client();
        client.getResilience().getRetry().setWaitDuration(Duration.ZERO);
        DefaultResilienceExecutor executor = executor("retry", client);
        AtomicInteger calls = new AtomicInteger();

        String value = executor.execute(context("retry"), () -> {
            if (calls.incrementAndGet() < 3) {
                throw retryable("retry");
            }
            return "ok";
        });

        assertThat(value).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(executor.circuitBreakerState("retry")).isEqualTo("CLOSED");
    }

    @Test
    void opensCircuitAndNormalizesRejection() {
        ClientProperties client = client();
        client.getResilience().getRetry().setEnabled(false);
        client.getResilience().getCircuitBreaker().setMinimumNumberOfCalls(1);
        client.getResilience().getCircuitBreaker().setSlidingWindowSize(2);
        DefaultResilienceExecutor executor = executor("circuit", client);

        assertThatThrownBy(() -> executor.execute(context("circuit"), () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(executor.circuitBreakerState("circuit")).isEqualTo("OPEN");
        assertThatThrownBy(() -> executor.execute(context("circuit"), () -> "never"))
            .isInstanceOf(OutboundCircuitOpenException.class);
    }

    @Test
    void rejectsOverRateLimitAndBypassesWhenResilienceDisabled() {
        ClientProperties client = client();
        client.getResilience().getRetry().setEnabled(false);
        client.getResilience().getCircuitBreaker().setEnabled(false);
        client.getResilience().getRateLimiter().setEnabled(true);
        client.getResilience().getRateLimiter().setLimitForPeriod(1);
        client.getResilience().getRateLimiter().setLimitRefreshPeriod(Duration.ofMinutes(1));
        DefaultResilienceExecutor executor = executor("limited", client);

        assertThat(executor.execute(context("limited"), () -> "first")).isEqualTo("first");
        assertThatThrownBy(() -> executor.execute(context("limited"), () -> "second"))
            .isInstanceOf(OutboundRateLimitException.class);

        ClientProperties disabled = client();
        disabled.getResilience().setEnabled(false);
        assertThat(executor("disabled", disabled).execute(
            context("disabled"), () -> "direct")).isEqualTo("direct");
    }

    private static DefaultResilienceExecutor executor(
        String name, ClientProperties client
    ) {
        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        properties.getClients().put(name, client);
        return new DefaultResilienceExecutor(new ClientConfigurationResolver(properties));
    }

    private static ClientProperties client() {
        ClientProperties client = new ClientProperties();
        client.setProtocol(ExchangeProtocol.HTTP);
        client.getHttp().setBaseUrl("https://example.test");
        return client;
    }

    private static ResilienceExecutionContext context(String client) {
        return ResilienceExecutionContext.builder().clientName(client).operation("GET").build();
    }

    private static OutboundHttpException retryable(String client) {
        return new OutboundHttpException(client, "GET", URI.create("https://example.test"),
            503, Map.of(), null, 0, Duration.ZERO, true, null);
    }
}
