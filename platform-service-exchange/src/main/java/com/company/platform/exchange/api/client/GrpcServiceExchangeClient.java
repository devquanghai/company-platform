package com.company.platform.exchange.api.client;

import java.util.function.Supplier;
import java.time.Duration;

/** Named gRPC logical-call facade; generated stubs remain application owned. */
public interface GrpcServiceExchangeClient extends ServiceExchangeClient {
    <T> T execute(
        String serviceName, String methodName, Class<T> responseType,
        boolean idempotent, Duration deadline, Supplier<T> invocation);
}
