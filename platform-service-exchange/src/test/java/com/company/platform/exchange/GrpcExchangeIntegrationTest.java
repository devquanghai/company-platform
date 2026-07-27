package com.company.platform.exchange;

import com.company.platform.exchange.adapter.outbound.grpc.DefaultGrpcChannelRegistry;
import com.company.platform.exchange.adapter.outbound.grpc.DefaultGrpcClientFactory;
import com.company.platform.exchange.api.grpc.GrpcCallRequest;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.application.service.DefaultGrpcCallOperations;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.GrpcNegotiationType;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.OutboundGrpcException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.resilience.executor.DefaultResilienceExecutor;
import com.company.platform.exchange.resilience.executor.DefaultRetryDecisionPolicy;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcExchangeIntegrationTest {

    private static final MethodDescriptor<String, String> METHOD =
        MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Echo/Echo")
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    private Server server;
    private ManagedChannel channel;
    private ClientConfigurationResolver resolver;
    private ServiceExchangeProperties properties;
    private ClientProperties client;

    @BeforeEach
    void setUp() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(ServerServiceDefinition.builder("test.Echo")
                .addMethod(METHOD, ServerCalls.asyncUnaryCall(
                    (request, observer) -> {
                        observer.onNext("echo:" + request);
                        observer.onCompleted();
                    })).build())
            .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        properties = new ServiceExchangeProperties();
        client = new ClientProperties();
        client.setProtocol(ExchangeProtocol.GRPC);
        client.getGrpc().setAddress("in-process:test");
        client.getGrpc().setNegotiationType(GrpcNegotiationType.PLAINTEXT);
        client.getResilience().getRetry().setWaitDuration(Duration.ZERO);
        client.getResilience().getCircuitBreaker().setEnabled(false);
        properties.getClients().put("grpc", client);
        resolver = new ClientConfigurationResolver(properties);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void createsNamedChannelAndGeneratedStyleStubWithoutOwningShutdown() {
        CapturingChannelFactory spring = new CapturingChannelFactory(channel);
        DefaultGrpcChannelRegistry registry =
            new DefaultGrpcChannelRegistry(resolver, spring, Optional.empty());
        DefaultGrpcClientFactory factory = new DefaultGrpcClientFactory(registry);

        TestStub stub = factory.createStub("grpc",
            value -> new TestStub(value, CallOptions.DEFAULT));
        String response = ClientCalls.blockingUnaryCall(
            stub.getChannel(), METHOD, stub.getCallOptions(), "hello");

        assertThat(response).isEqualTo("echo:hello");
        assertThat(factory.getChannel("grpc")).isSameAs(channel);
        assertThat(spring.target).isEqualTo("in-process:test");
        assertThat(spring.options.interceptors()).isNotEmpty();
        assertThat(channel.isShutdown()).isFalse();
    }

    @Test
    void grpcOperationsRetryConfiguredStatusAndIgnoreBusinessStatus() throws Exception {
        DefaultGrpcCallOperations operations = new DefaultGrpcCallOperations(
            resolver, new DefaultRetryDecisionPolicy(resolver),
            new DefaultResilienceExecutor(resolver));
        AtomicInteger calls = new AtomicInteger();
        try (operations) {
            String value = operations.execute(GrpcCallRequest.builder()
                .clientName("grpc")
                .serviceName("test.Echo")
                .methodName("Echo")
                .idempotent(true)
                .build(), () -> {
                    if (calls.incrementAndGet() < 3) {
                        throw Status.UNAVAILABLE.asRuntimeException();
                    }
                    return "ok";
                });
            assertThat(value).isEqualTo("ok");
            assertThat(calls).hasValue(3);

            assertThatThrownBy(() -> operations.execute(
                "grpc", "test.Echo", "Missing",
                () -> { throw Status.NOT_FOUND.asRuntimeException(); }))
                .isInstanceOf(OutboundGrpcException.class)
                .extracting("status").isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    @Test
    void appliesPlaintextTlsTrustAllAuthorityAndHttpConnectProxyOptions() {
        CapturingChannelFactory spring = new CapturingChannelFactory(channel);
        DefaultGrpcChannelRegistry registry =
            new DefaultGrpcChannelRegistry(resolver, spring, Optional.empty());
        client.getGrpc().setAuthorityOverride("test-authority");
        client.getProxy().setEnabled(true);
        client.getProxy().setHost("localhost");
        client.getProxy().setPort(8080);

        assertThat(registry.getChannel("grpc")).isSameAs(channel);
        assertThat(spring.customizerApplied).isTrue();

        ServiceExchangeProperties tlsProperties = new ServiceExchangeProperties();
        ClientProperties tls = new ClientProperties();
        tls.setProtocol(ExchangeProtocol.GRPC);
        tls.getGrpc().setAddress("dns:///localhost:443");
        tls.getGrpc().setNegotiationType(GrpcNegotiationType.TLS);
        tls.getSsl().setEnabled(true);
        tls.getSsl().setTrustAll(true);
        tls.getSsl().setHostnameVerificationEnabled(false);
        tlsProperties.getClients().put("tls", tls);
        DefaultGrpcChannelRegistry tlsRegistry = new DefaultGrpcChannelRegistry(
            new ClientConfigurationResolver(tlsProperties),
            new CapturingChannelFactory(channel), Optional.empty());
        assertThat(tlsRegistry.getChannel("tls")).isSameAs(channel);
    }

    @Test
    void rejectsUnsupportedGrpcProxySchemeAndMissingMtlsBundle() {
        client.getProxy().setEnabled(true);
        client.getProxy().setScheme("socks");
        client.getProxy().setHost("localhost");
        client.getProxy().setPort(1080);
        DefaultGrpcChannelRegistry proxyRegistry = new DefaultGrpcChannelRegistry(
            resolver, new CapturingChannelFactory(channel), Optional.empty());
        assertThatThrownBy(() -> proxyRegistry.getChannel("grpc"))
            .isInstanceOf(com.company.platform.exchange.domain.exception
                .InvalidClientConfigurationException.class);

        ServiceExchangeProperties mtlsProperties = new ServiceExchangeProperties();
        ClientProperties mtls = new ClientProperties();
        mtls.setProtocol(ExchangeProtocol.GRPC);
        mtls.getGrpc().setAddress("dns:///localhost:443");
        mtls.getGrpc().setNegotiationType(GrpcNegotiationType.MTLS);
        mtls.getSsl().setEnabled(true);
        mtls.getSsl().setBundle("missing");
        mtlsProperties.getClients().put("mtls", mtls);
        DefaultGrpcChannelRegistry mtlsRegistry = new DefaultGrpcChannelRegistry(
            new ClientConfigurationResolver(mtlsProperties),
            new CapturingChannelFactory(channel), Optional.empty());
        assertThatThrownBy(() -> mtlsRegistry.getChannel("mtls"))
            .isInstanceOf(com.company.platform.exchange.domain.exception
                .InvalidClientConfigurationException.class);
    }

    private static final class CapturingChannelFactory implements GrpcChannelFactory {
        private final ManagedChannel channel;
        private String target;
        private ChannelBuilderOptions options;
        private boolean customizerApplied;

        private CapturingChannelFactory(ManagedChannel channel) {
            this.channel = channel;
        }
        @Override public boolean supports(String target) { return true; }
        @Override public ManagedChannel createChannel(
            String target, ChannelBuilderOptions options
        ) {
            this.target = target;
            this.options = options;
            NettyChannelBuilder builder = NettyChannelBuilder.forTarget("localhost:1");
            options.<NettyChannelBuilder>customizer().customize(target, builder);
            customizerApplied = true;
            return channel;
        }
    }

    private static final class TestStub extends AbstractStub<TestStub> {
        private TestStub(Channel channel, CallOptions options) {
            super(channel, options);
        }
        @Override protected TestStub build(Channel channel, CallOptions options) {
            return new TestStub(channel, options);
        }
    }

    private static final class StringMarshaller
        implements MethodDescriptor.Marshaller<String> {
        @Override public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }
        @Override public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
