package com.company.platform.exchange.client.internal.adapter;

import com.company.platform.exchange.api.client.BlockingServiceExchangeClient;
import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
import com.company.platform.exchange.domain.exception.SanitizedRemoteCauseException;
import com.company.platform.exchange.client.internal.application.ClientCallLifecycle;
import com.company.platform.exchange.resilience.executor.ResilienceExecutionContext;
import com.company.platform.exchange.resilience.executor.ResilienceExecutor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.util.function.Supplier;

public final class DefaultBlockingServiceExchangeClient
    implements BlockingServiceExchangeClient {
    private final String name;
    private final RestClient client;
    private final ResilienceExecutor resilience;
    private final ObservationRegistry observations;
    private final ClientCallLifecycle lifecycle;

    public DefaultBlockingServiceExchangeClient(
        String name,
        RestClient client,
        ResilienceExecutor resilience,
        ObservationRegistry observations,
        ClientCallLifecycle lifecycle
    ) {
        this.name = name;
        this.client = client;
        this.resilience = resilience;
        this.observations = observations;
        this.lifecycle = lifecycle;
    }

    @Override public String name() { return name; }
    @Override public ServiceExchangeClientType type() {
        return ServiceExchangeClientType.RESTCLIENT;
    }

    public RestClient restClient() {
        return client;
    }

    @Override
    public <T> T get(String uri, Class<T> responseType) {
        validateRelativeUri(uri);
        return execute("GET", responseType,
            () -> client.get().uri(uri).retrieve().body(responseType));
    }

    @Override
    public <B, T> T post(String uri, B body, Class<T> responseType) {
        validateRelativeUri(uri);
        return execute("POST", responseType, () -> client.post().uri(uri).body(body)
            .retrieve().body(responseType));
    }

    private <T> T execute(
        String method, Class<T> responseType, Supplier<T> invocation
    ) {
        ClientCallLifecycle.CallState state = lifecycle.start(method);
        Observation observation = Observation.createNotStarted(
            "platform.service.exchange", observations)
            .lowCardinalityKeyValue("client.name", name)
            .lowCardinalityKeyValue("http.method", method);
        try {
            Supplier<T> normalized = () -> {
                try {
                    return invocation.get();
                } catch (RestClientResponseException failure) {
                    throw map(method, failure.getStatusCode().value(), failure);
                } catch (ResourceAccessException failure) {
                    throw mapTransport(method, failure);
                } catch (ServiceExchangeClientException failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw mapProgramming(method, failure);
                }
            };
            T value = observation.observe(() -> resilience.execute(
                ResilienceExecutionContext.builder().clientName(name)
                    .operation(method).build(), normalized));
            lifecycle.success(state);
            return value;
        } catch (RuntimeException failure) {
            return lifecycle.failure(state, responseType, failure);
        }
    }

    private ServiceExchangeClientException map(
        String method, Integer status, RuntimeException failure
    ) {
        boolean safeMethod = "GET".equals(method);
        boolean retryable = safeMethod && (status == 408 || status == 502
            || status == 503 || status == 504);
        boolean recordable = status == 408 || status >= 500;
        return new ServiceExchangeClientException(
            name, method, status, retryable, recordable,
            new SanitizedRemoteCauseException(failure));
    }

    private ServiceExchangeClientException mapTransport(
        String method, RuntimeException failure
    ) {
        return new ServiceExchangeClientException(
            name, method, null, "GET".equals(method), true,
            new SanitizedRemoteCauseException(failure));
    }

    private ServiceExchangeClientException mapProgramming(
        String method, RuntimeException failure
    ) {
        return new ServiceExchangeClientException(
            name, method, null, false, false,
            new SanitizedRemoteCauseException(failure));
    }

    private void validateRelativeUri(String uri) {
        URI value = URI.create(uri);
        if (value.isAbsolute() || value.getHost() != null
            || value.getRawUserInfo() != null || value.getRawFragment() != null
            || uri.startsWith("//")) {
            throw new IllegalArgumentException("Only relative request URIs are allowed");
        }
    }
}
