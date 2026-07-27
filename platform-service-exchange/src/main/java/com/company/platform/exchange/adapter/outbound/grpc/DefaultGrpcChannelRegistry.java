package com.company.platform.exchange.adapter.outbound.grpc;

import com.company.platform.exchange.api.grpc.GrpcChannelRegistry;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.GrpcNegotiationType;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.model.ProxyCustomizationContext;
import com.company.platform.exchange.domain.model.ProxyEndpoint;
import com.company.platform.exchange.domain.policy.ClientProxyCustomizer;
import io.grpc.Channel;
import io.grpc.HttpConnectProxiedSocketAddress;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.ProxyDetector;
import io.grpc.stub.MetadataUtils;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

public final class DefaultGrpcChannelRegistry implements GrpcChannelRegistry {

    private static final Metadata.Key<String> CLIENT_HEADER =
        Metadata.Key.of("x-platform-client", Metadata.ASCII_STRING_MARSHALLER);

    private final ClientConfigurationResolver resolver;
    private final GrpcChannelFactory channelFactory;
    private final SslBundles sslBundles;
    private final ClientProxyCustomizer proxyCustomizer;
    private final Duration shutdownTimeout;
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    public DefaultGrpcChannelRegistry(
        ClientConfigurationResolver resolver, GrpcChannelFactory channelFactory,
        Optional<SslBundles> sslBundles
    ) {
        this(resolver, channelFactory, sslBundles,
            (context, configured) -> configured, Duration.ofSeconds(10));
    }

    public DefaultGrpcChannelRegistry(
        ClientConfigurationResolver resolver, GrpcChannelFactory channelFactory,
        Optional<SslBundles> sslBundles, ClientProxyCustomizer proxyCustomizer,
        Duration shutdownTimeout
    ) {
        this.resolver = resolver;
        this.channelFactory = channelFactory;
        this.sslBundles = sslBundles.orElse(null);
        this.proxyCustomizer = proxyCustomizer;
        this.shutdownTimeout = shutdownTimeout;
    }

    @Override
    public Channel getChannel(String clientName) {
        ClientProperties client = resolver.resolve(clientName, ExchangeProtocol.GRPC);
        return channels.computeIfAbsent(clientName, ignored -> create(clientName, client));
    }

    private ManagedChannel create(String name, ClientProperties client) {
        Metadata metadata = new Metadata();
        metadata.put(CLIENT_HEADER, name);
        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
            .withShutdownGracePeriod(shutdownTimeout)
            .withInterceptors(List.of(MetadataUtils.newAttachHeadersInterceptor(metadata)))
            .withCustomizer((target, builder) -> {
                var grpc = client.getGrpc();
                builder.maxInboundMessageSize(
                    Math.toIntExact(grpc.getMaxInboundMessageSize().toBytes()));
                builder.keepAliveTime(grpc.getKeepAliveTime().toNanos(), TimeUnit.NANOSECONDS);
                builder.keepAliveTimeout(
                    grpc.getKeepAliveTimeout().toNanos(), TimeUnit.NANOSECONDS);
                builder.keepAliveWithoutCalls(grpc.isKeepAliveWithoutCalls());
                if (StringUtils.hasText(grpc.getAuthorityOverride())) {
                    builder.overrideAuthority(grpc.getAuthorityOverride());
                }
                if (client.getProxy().isEnabled()) {
                    builder.proxyDetector(proxy(name, client));
                }
                configureSecurity(name, client, builder);
            });
        try {
            return channelFactory.createChannel(client.getGrpc().getAddress(), options);
        } catch (RuntimeException exception) {
            throw new InvalidClientConfigurationException(
                name, "gRPC channel creation failed: " + exception.getClass().getSimpleName());
        }
    }

    private void configureSecurity(
        String name, ClientProperties client, io.grpc.ManagedChannelBuilder<?> builder
    ) {
        if (client.getGrpc().getNegotiationType() == GrpcNegotiationType.PLAINTEXT) {
            builder.usePlaintext();
            return;
        }
        if (!(builder instanceof NettyChannelBuilder netty)) {
            throw new InvalidClientConfigurationException(
                name, "TLS/mTLS requires shaded Netty transport");
        }
        try {
            var ssl = GrpcSslContexts.forClient();
            if (client.getSsl().isEnabled() && client.getSsl().isTrustAll()) {
                ssl.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else if (client.getSsl().isEnabled()
                && StringUtils.hasText(client.getSsl().getBundle())) {
                SslBundle bundle = sslBundles.getBundle(client.getSsl().getBundle());
                ssl.trustManager(bundle.getManagers().getTrustManagerFactory());
                if (client.getGrpc().getNegotiationType() == GrpcNegotiationType.MTLS) {
                    ssl.keyManager(bundle.getManagers().getKeyManagerFactory());
                }
                String[] protocols = bundle.getOptions().getEnabledProtocols();
                String[] ciphers = bundle.getOptions().getCiphers();
                if (protocols != null && protocols.length > 0) {
                    ssl.protocols(protocols);
                }
                if (ciphers != null && ciphers.length > 0) {
                    ssl.ciphers(List.of(ciphers));
                }
            }
            ssl.endpointIdentificationAlgorithm(
                client.getSsl().isHostnameVerificationEnabled() ? "HTTPS" : "");
            netty.sslContext(ssl.build()).useTransportSecurity();
        } catch (Exception exception) {
            throw new InvalidClientConfigurationException(
                name, "gRPC TLS context creation failed: "
                    + exception.getClass().getSimpleName());
        }
    }

    private ProxyDetector proxy(String clientName, ClientProperties client) {
        ProxyEndpoint configured = ProxyEndpoint.builder()
            .scheme(client.getProxy().getScheme()).host(client.getProxy().getHost())
            .port(client.getProxy().getPort()).username(client.getProxy().getUsername())
            .password(client.getProxy().getPassword()).build();
        ProxyEndpoint customized = proxyCustomizer.customize(
            ProxyCustomizationContext.builder().clientName(clientName)
                .protocol(ExchangeProtocol.GRPC)
                .target(client.getGrpc().getAddress()).build(), configured);
        if (customized == null) {
            return target -> null;
        }
        if (!"http".equalsIgnoreCase(customized.getScheme())) {
            throw new InvalidClientConfigurationException(
                "grpc-proxy", "only HTTP CONNECT proxy is supported");
        }
        return target -> {
            if (!(target instanceof InetSocketAddress address)) {
                throw new java.io.IOException("gRPC proxy requires an internet socket target");
            }
            if (isNonProxyHost(address.getHostString(), client.getProxy().getNonProxyHosts())) {
                return null;
            }
            return HttpConnectProxiedSocketAddress.newBuilder()
                .setProxyAddress(new InetSocketAddress(
                    customized.getHost(), customized.getPort()))
                .setTargetAddress(address)
                .setUsername(customized.getUsername())
                .setPassword(customized.getPassword())
                .build();
        };
    }

    private static boolean isNonProxyHost(String host, List<String> patterns) {
        if (host == null) {
            return false;
        }
        String candidate = host.toLowerCase(java.util.Locale.ROOT);
        return patterns.stream().filter(StringUtils::hasText).anyMatch(pattern -> {
            String normalized = pattern.toLowerCase(java.util.Locale.ROOT);
            return normalized.equals(candidate)
                || normalized.startsWith("*.")
                    && (candidate.equals(normalized.substring(2))
                        || candidate.endsWith(normalized.substring(1)));
        });
    }
}
