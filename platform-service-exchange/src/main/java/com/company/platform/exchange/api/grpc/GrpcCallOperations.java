package com.company.platform.exchange.api.grpc;

import java.util.function.Supplier;

public interface GrpcCallOperations {
    <T> T execute(
        String clientName, String serviceName, String methodName, Supplier<T> invocation);

    <T> T execute(GrpcCallRequest request, Supplier<T> invocation);
}
