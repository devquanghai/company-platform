package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import reactor.core.publisher.Mono;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;

/** Reactive fail-fast bridge used when native Resilience4j registries are absent. */
public final class UnavailableReactiveResilienceExecutor
    implements ReactiveResilienceExecutor {
    private final ClientConfigurationResolver resolver;

    public UnavailableReactiveResilienceExecutor(
        ClientConfigurationResolver resolver
    ) {
        this.resolver = resolver;
    }

    @Override
    public <T> Mono<T> execute(String clientName, Mono<T> invocation) {
        if (!resolver.resolve(clientName).isResilienceEnabled()) {
            return invocation;
        }
        return Mono.error(new InvalidClientConfigurationException(
            clientName,
            "resilience is enabled but native reactive Resilience4j registries are unavailable"));
    }
}
