package com.company.platform.exchange.client.internal.adapter;

import com.company.platform.exchange.api.client.ReactiveServiceExchangeClient;
import com.company.platform.exchange.api.client.ServiceExchangeClientType;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
import com.company.platform.exchange.domain.exception.SanitizedRemoteCauseException;
import com.company.platform.exchange.resilience.executor.ReactiveResilienceExecutor;
import com.company.platform.exchange.client.internal.application.ClientCallLifecycle;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.URI;

public final class DefaultReactiveServiceExchangeClient
    implements ReactiveServiceExchangeClient {
    private final String name;
    private final WebClient client;
    private final ReactiveResilienceExecutor resilience;
    private final ObservationRegistry observations;
    private final ClientCallLifecycle lifecycle;

    public DefaultReactiveServiceExchangeClient(
        String name,
        WebClient client,
        ReactiveResilienceExecutor resilience,
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
        return ServiceExchangeClientType.WEBCLIENT;
    }

    @Override
    public <T> Mono<T> get(String uri, Class<T> responseType) {
        validateRelativeUri(uri);
        return execute("GET", responseType,
            client.get().uri(uri).retrieve().bodyToMono(responseType));
    }

    @Override
    public <B, T> Mono<T> post(String uri, B body, Class<T> responseType) {
        validateRelativeUri(uri);
        return execute("POST", responseType, client.post().uri(uri).bodyValue(body)
            .retrieve().bodyToMono(responseType));
    }

    private <T> Mono<T> execute(
        String method, Class<T> responseType, Mono<T> invocation
    ) {
        return Mono.defer(() -> {
            ClientCallLifecycle.CallState state = lifecycle.start(method);
            Observation observation = Observation.createNotStarted(
                "platform.service.exchange", observations)
                .lowCardinalityKeyValue("client.name", name)
                .lowCardinalityKeyValue("http.method", method).start();
            Mono<T> normalized = invocation
                .onErrorMap(WebClientResponseException.class,
                    failure -> map(method, failure.getStatusCode().value(), failure))
                .onErrorMap(WebClientRequestException.class,
                    failure -> mapTransport(method, failure))
                .onErrorMap(failure -> !(failure instanceof ServiceExchangeClientException),
                    failure -> mapProgramming(method, failure));
            return resilience.execute(name, normalized)
                .doOnSuccess(ignored -> lifecycle.success(state))
                .onErrorResume(RuntimeException.class, failure -> Mono.fromCallable(
                    () -> lifecycle.failure(state, responseType, failure)))
                .doOnError(observation::error)
                .doFinally(ignored -> observation.stop());
        });
    }

    private ServiceExchangeClientException map(
        String method, Integer status, Throwable failure
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
        String method, Throwable failure
    ) {
        return new ServiceExchangeClientException(
            name, method, null, "GET".equals(method), true,
            new SanitizedRemoteCauseException(failure));
    }

    private ServiceExchangeClientException mapProgramming(
        String method, Throwable failure
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
