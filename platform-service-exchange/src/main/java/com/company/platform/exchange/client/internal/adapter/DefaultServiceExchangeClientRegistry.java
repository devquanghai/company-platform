package com.company.platform.exchange.client.internal.adapter;

import com.company.platform.exchange.api.client.ServiceExchangeClient;
import com.company.platform.exchange.api.client.ServiceExchangeClientRegistry;
import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import com.company.platform.exchange.api.client.GrpcServiceExchangeClient;
import com.company.platform.exchange.api.grpc.GrpcCallOperations;
import com.company.platform.exchange.api.grpc.GrpcCallRequest;
import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.api.customize.ServiceExchangeClientCustomization;
import com.company.platform.exchange.api.customize.ServiceExchangeClientCustomizer;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;
import com.company.platform.exchange.client.internal.application.ClientCallLifecycle;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackRegistry;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.trace.TraceContextProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.boot.webclient.autoconfigure.WebClientSsl;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.net.URI;

import reactor.core.publisher.Mono;

public final class DefaultServiceExchangeClientRegistry
    implements ServiceExchangeClientRegistry, HttpClientRegistry {
    private final ClientConfigurationResolver resolver;
    private final ObjectProvider<RestClient.Builder> restBuilders;
    private final ObjectProvider<WebClient.Builder> webBuilders;
    private final RestClientSsl restSsl;
    private final WebClientSsl webSsl;
    private final GrpcCallOperations grpcCalls;
    private final ObservationRegistry observations;
    private final ResilienceExecutor blockingResilience;
    private final ReactiveResilienceExecutor reactiveResilience;
    private final List<ServiceExchangeClientCustomizer> customizers;
    private final OutboundFallbackRegistry fallbacks;
    private final OutboundCallEventPublisher events;
    private final TimeProvider time;
    private final RequestContextProvider requestContext;
    private final TraceContextProvider traceContext;
    private final String sourceApplication;
    private final ConcurrentHashMap<String, ServiceExchangeClient> clients =
        new ConcurrentHashMap<>();

    public DefaultServiceExchangeClientRegistry(
        ClientConfigurationResolver resolver,
        ObjectProvider<RestClient.Builder> restBuilders,
        ObjectProvider<WebClient.Builder> webBuilders,
        ObjectProvider<RestClientSsl> restSsl,
        ObjectProvider<WebClientSsl> webSsl,
        ObjectProvider<GrpcCallOperations> grpcCalls,
        ObjectProvider<ObservationRegistry> observations,
        ResilienceExecutor blockingResilience,
        ReactiveResilienceExecutor reactiveResilience,
        List<ServiceExchangeClientCustomizer> customizers,
        OutboundFallbackRegistry fallbacks,
        OutboundCallEventPublisher events,
        TimeProvider time,
        RequestContextProvider requestContext,
        TraceContextProvider traceContext,
        String sourceApplication
    ) {
        this.resolver = resolver;
        this.restBuilders = restBuilders;
        this.webBuilders = webBuilders;
        this.restSsl = restSsl.getIfAvailable();
        this.webSsl = webSsl.getIfAvailable();
        this.grpcCalls = grpcCalls.getIfAvailable();
        this.observations = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.blockingResilience = blockingResilience;
        this.reactiveResilience = reactiveResilience;
        this.customizers = List.copyOf(customizers);
        this.fallbacks = fallbacks;
        this.events = events;
        this.time = time;
        this.requestContext = requestContext;
        this.traceContext = traceContext;
        this.sourceApplication = sourceApplication;
        resolver.clients().forEach((name, client) -> {
            if (client.isEnabled()) {
                clients.put(name, create(name, client));
            }
        });
    }

    @Override
    public ServiceExchangeClient get(String name) {
        ClientProperties client = resolver.resolve(name);
        return clients.computeIfAbsent(name, ignored -> create(name, client));
    }

    @Override
    public Optional<ServiceExchangeClient> find(String name) {
        ClientProperties configured = resolver.clients().get(name);
        return configured == null || !configured.isEnabled()
            ? Optional.empty() : Optional.of(get(name));
    }

    @Override
    public boolean contains(String name) {
        ClientProperties client = resolver.clients().get(name);
        return client != null && client.isEnabled();
    }

    @Override
    public RestClient getClient(String clientName) {
        ServiceExchangeClient client = get(clientName);
        if (client instanceof DefaultBlockingServiceExchangeClient blocking) {
            return blocking.restClient();
        }
        throw new InvalidClientConfigurationException(
            clientName, "client is not a RESTCLIENT");
    }

    private ServiceExchangeClient create(String name, ClientProperties client) {
        return switch (client.getType()) {
            case RESTCLIENT -> createBlocking(name, client);
            case WEBCLIENT -> createReactive(name, client);
            case GRPC -> createGrpc(name);
        };
    }

    private ServiceExchangeClient createBlocking(String name, ClientProperties client) {
        RestClient.Builder builder = restBuilders.getIfAvailable();
        if (builder == null) {
            throw missing(name, "Boot-managed RestClient.Builder");
        }
        builder = builder.clone().baseUrl(client.getBaseUrl());
        if (StringUtils.hasText(client.getSslBundle())) {
            if (restSsl == null) {
                throw missing(name, "Boot RestClientSsl");
            }
            builder.apply(restSsl.fromBundle(client.getSslBundle()));
        }
        ObservationRegistry registry = observationRegistry(client);
        builder.observationRegistry(registry);
        List<HeaderValue> headers = customize(name, ServiceExchangeClientType.RESTCLIENT);
        builder.requestInterceptor((request, body, execution) -> {
            headers.forEach(header -> request.getHeaders().set(
                header.name(), header.value().get()));
            return execution.execute(request, body);
        });
        URI origin = URI.create(client.getBaseUrl());
        builder.requestInterceptor((request, body, execution) -> {
            if (!sameOrigin(origin, request.getURI())) {
                throw new IllegalArgumentException(
                    "Outbound request must remain on the configured client origin");
            }
            return execution.execute(request, body);
        });
        return new DefaultBlockingServiceExchangeClient(
            name, builder.build(), blockingResilience(client, name), registry,
            lifecycle(name, client));
    }

    private ServiceExchangeClient createReactive(String name, ClientProperties client) {
        WebClient.Builder builder = webBuilders.getIfAvailable();
        if (builder == null) {
            throw missing(name, "Boot-managed WebClient.Builder");
        }
        builder = builder.clone().baseUrl(client.getBaseUrl());
        if (StringUtils.hasText(client.getSslBundle())) {
            if (webSsl == null) {
                throw missing(name, "Boot WebClientSsl");
            }
            builder.apply(webSsl.fromBundle(client.getSslBundle()));
        }
        ObservationRegistry registry = observationRegistry(client);
        builder.observationRegistry(registry);
        List<HeaderValue> headers = customize(name, ServiceExchangeClientType.WEBCLIENT);
        builder.filter((request, next) -> next.exchange(
            org.springframework.web.reactive.function.client.ClientRequest.from(request)
                .headers(values -> headers.forEach(header -> values.set(
                    header.name(), header.value().get())))
                .build()));
        return new DefaultReactiveServiceExchangeClient(
            name, builder.build(), reactiveResilience(client, name), registry,
            lifecycle(name, client));
    }

    private ServiceExchangeClient createGrpc(String name) {
        if (grpcCalls == null) {
            throw missing(name, "GrpcCallOperations");
        }
        return new GrpcClientReference(name);
    }

    private ObservationRegistry observationRegistry(ClientProperties client) {
        return client.isObservabilityEnabled() ? observations : ObservationRegistry.NOOP;
    }

    private ClientCallLifecycle lifecycle(String name, ClientProperties client) {
        return new ClientCallLifecycle(
            name, client, fallbacks, events, blockingResilience(client, name),
            time, requestContext, traceContext, sourceApplication);
    }

    private List<HeaderValue> customize(
        String name, ServiceExchangeClientType type
    ) {
        List<HeaderValue> headers = new ArrayList<>();
        ServiceExchangeClientCustomization target = new ServiceExchangeClientCustomization() {
            @Override public String clientName() { return name; }
            @Override public ServiceExchangeClientType clientType() { return type; }
            @Override public void defaultHeader(String header, Supplier<String> value) {
                if (header == null || header.isBlank() || value == null) {
                    throw new IllegalArgumentException("Header name and value supplier are required");
                }
                headers.add(new HeaderValue(header, value));
            }
        };
        customizers.stream().filter(customizer -> customizer.supports(name))
            .forEach(customizer -> customizer.customize(target));
        return List.copyOf(headers);
    }

    private ResilienceExecutor blockingResilience(
        ClientProperties client, String name
    ) {
        if (!client.isResilienceEnabled()) {
            return new ResilienceExecutor() {
                @Override
                public <T> T execute(
                    com.company.platform.exchange.resilience.executor.ResilienceExecutionContext context,
                    Supplier<T> invocation
                ) {
                    return invocation.get();
                }

                @Override
                public String circuitBreakerState(String clientName) {
                    return "DISABLED";
                }
            };
        }
        if (blockingResilience == null) {
            throw missing(name, "native Resilience4j registries");
        }
        return blockingResilience;
    }

    private ReactiveResilienceExecutor reactiveResilience(
        ClientProperties client, String name
    ) {
        if (!client.isResilienceEnabled()) {
            return new ReactiveResilienceExecutor() {
                @Override
                public <T> Mono<T> execute(String clientName, Mono<T> invocation) {
                    return invocation;
                }
            };
        }
        if (reactiveResilience == null) {
            throw missing(name, "native reactive Resilience4j registries");
        }
        return reactiveResilience;
    }

    private InvalidClientConfigurationException missing(String name, String capability) {
        return new InvalidClientConfigurationException(
            name, capability + " is unavailable");
    }

    private boolean sameOrigin(URI configured, URI request) {
        return configured.getScheme().equalsIgnoreCase(request.getScheme())
            && configured.getHost().equalsIgnoreCase(request.getHost())
            && effectivePort(configured) == effectivePort(request);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private final class GrpcClientReference
        implements GrpcServiceExchangeClient {
        private final String name;

        private GrpcClientReference(String name) { this.name = name; }

        @Override public String name() { return name; }
        @Override public ServiceExchangeClientType type() {
            return ServiceExchangeClientType.GRPC;
        }

        @Override
        public <T> T execute(
            String serviceName, String methodName, Class<T> responseType,
            boolean idempotent, java.time.Duration deadline, Supplier<T> invocation
        ) {
            return grpcCalls.execute(GrpcCallRequest.builder()
                .clientName(name).serviceName(serviceName).methodName(methodName)
                .responseType(responseType).idempotent(idempotent)
                .deadline(deadline).build(), invocation);
        }
    }

    private record HeaderValue(String name, Supplier<String> value) { }
}
