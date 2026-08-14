package com.company.platform.exchange.api.client;

public interface BlockingServiceExchangeClient extends ServiceExchangeClient {
    <T> T get(String uri, Class<T> responseType);

    <B, T> T post(String uri, B body, Class<T> responseType);
}
