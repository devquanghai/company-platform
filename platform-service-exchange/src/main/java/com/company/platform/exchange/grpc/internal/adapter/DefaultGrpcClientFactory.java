package com.company.platform.exchange.grpc.internal.adapter;

import com.company.platform.exchange.api.grpc.GrpcChannelRegistry;
import com.company.platform.exchange.api.grpc.GrpcClientFactory;
import io.grpc.Channel;
import io.grpc.stub.AbstractStub;

import java.util.Objects;
import java.util.function.Function;

public final class DefaultGrpcClientFactory implements GrpcClientFactory {

    private final GrpcChannelRegistry channels;

    public DefaultGrpcClientFactory(GrpcChannelRegistry channels) {
        this.channels = channels;
    }

    @Override
    public Channel getChannel(String clientName) {
        return channels.getChannel(clientName);
    }

    @Override
    public <S extends AbstractStub<S>> S createStub(
        String clientName, Function<Channel, S> stubFactory
    ) {
        return Objects.requireNonNull(stubFactory, "stubFactory")
            .apply(getChannel(clientName));
    }
}
