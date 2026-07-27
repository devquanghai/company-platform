package com.company.platform.exchange.api.grpc;

import io.grpc.Channel;
import io.grpc.stub.AbstractStub;

import java.util.function.Function;

public interface GrpcClientFactory {
    Channel getChannel(String clientName);

    <S extends AbstractStub<S>> S createStub(
        String clientName, Function<Channel, S> stubFactory);
}
