package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;

import java.util.function.Supplier;

/** Fail-fast bridge used only when native Resilience4j registries are absent. */
public final class UnavailableResilienceExecutor implements ResilienceExecutor {
    private final ClientConfigurationResolver resolver;

    public UnavailableResilienceExecutor(ClientConfigurationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public <T> T execute(ResilienceExecutionContext context, Supplier<T> invocation) {
        ClientProperties client = resolver.resolve(context.getClientName());
        if (!client.isResilienceEnabled()) {
            return invocation.get();
        }
        throw new InvalidClientConfigurationException(
            context.getClientName(),
            "resilience is enabled but native Resilience4j registries are unavailable");
    }

    @Override
    public String circuitBreakerState(String clientName) {
        return resolver.resolve(clientName).isResilienceEnabled()
            ? "UNAVAILABLE" : "DISABLED";
    }
}
