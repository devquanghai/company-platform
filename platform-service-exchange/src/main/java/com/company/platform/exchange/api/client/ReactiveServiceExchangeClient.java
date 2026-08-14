package com.company.platform.exchange.api.client;

import reactor.core.publisher.Mono;

public interface ReactiveServiceExchangeClient extends ServiceExchangeClient {
    <T> Mono<T> get(String uri, Class<T> responseType);

    <B, T> Mono<T> post(String uri, B body, Class<T> responseType);
}
