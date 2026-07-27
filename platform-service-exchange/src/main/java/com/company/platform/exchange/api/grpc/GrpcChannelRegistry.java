package com.company.platform.exchange.api.grpc;

import io.grpc.Channel;

public interface GrpcChannelRegistry {
    Channel getChannel(String clientName);
}
