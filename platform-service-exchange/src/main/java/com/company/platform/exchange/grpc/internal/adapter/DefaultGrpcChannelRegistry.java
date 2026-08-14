package com.company.platform.exchange.grpc.internal.adapter;

import com.company.platform.exchange.api.grpc.GrpcChannelRegistry;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultGrpcChannelRegistry implements GrpcChannelRegistry {
    private static final Metadata.Key<String> CLIENT_HEADER =
        Metadata.Key.of("x-platform-client", Metadata.ASCII_STRING_MARSHALLER);

    private final ClientConfigurationResolver resolver;
    private final GrpcChannelFactory channelFactory;
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    public DefaultGrpcChannelRegistry(
        ClientConfigurationResolver resolver,
        GrpcChannelFactory channelFactory
    ) {
        this.resolver = resolver;
        this.channelFactory = channelFactory;
    }

    @Override
    public Channel getChannel(String clientName) {
        ClientProperties client = resolver.resolve(clientName, ExchangeProtocol.GRPC);
        return channels.computeIfAbsent(clientName, ignored -> create(clientName, client));
    }

    private Channel create(String name, ClientProperties client) {
        Metadata metadata = new Metadata();
        metadata.put(CLIENT_HEADER, name);
        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
            .withInterceptors(List.of(MetadataUtils.newAttachHeadersInterceptor(metadata)));
        return channelFactory.createChannel(client.getGrpcChannel(), options);
    }
}
