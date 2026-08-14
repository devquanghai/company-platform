package com.company.platform.exchange.resilience.executor;

import reactor.core.publisher.Mono;

public interface ReactiveResilienceExecutor {
    <T> Mono<T> execute(String clientName, Mono<T> invocation);
}
