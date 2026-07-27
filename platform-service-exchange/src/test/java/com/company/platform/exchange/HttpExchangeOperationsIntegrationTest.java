package com.company.platform.exchange;

import com.company.platform.core.context.MdcRequestContextProvider;
import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.exchange.adapter.outbound.http.DefaultHttpClientRegistry;
import com.company.platform.exchange.adapter.outbound.http.SecureUriResolver;
import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.application.service.DefaultHttpExchangeOperations;
import com.company.platform.exchange.audit.event.OutboundCallEvent;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.observability.logging.DefaultOutboundDataMasker;
import com.company.platform.exchange.resilience.executor.DefaultResilienceExecutor;
import com.company.platform.exchange.resilience.executor.DefaultRetryDecisionPolicy;
import com.company.platform.exchange.resilience.fallback.DefaultOutboundFallbackRegistry;
import com.company.platform.exchange.resilience.fallback.ExchangeFallback;
import com.company.platform.exchange.resilience.fallback.FallbackContext;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeOperationsIntegrationTest {

    private HttpServer server;
    private ServiceExchangeProperties properties;
    private ClientProperties client;
    private final List<OutboundCallEvent> events = new ArrayList<>();
    private final AtomicInteger retries = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/method", this::method);
        server.createContext("/list", exchange -> respond(
            exchange, 200, "application/json", "[\"a\",\"b\"]"));
        server.createContext("/retry", exchange -> {
            int attempt = retries.incrementAndGet();
            respond(exchange, attempt < 3 ? 503 : 200, "text/plain",
                attempt < 3 ? "temporary" : "recovered");
        });
        server.createContext("/fail", exchange ->
            respond(exchange, 503, "application/json", "{\"password\":\"raw\"}"));
        server.start();

        properties = new ServiceExchangeProperties();
        client = new ClientProperties();
        client.setProtocol(ExchangeProtocol.HTTP);
        client.getHttp().setBaseUrl(
            "http://localhost:" + server.getAddress().getPort());
        client.getResilience().setEnabled(false);
        properties.getClients().put("local", client);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void supportsAllRestVerbsAndGenericResponses() throws Exception {
        try (TestOperations test = operations(List.of())) {
            assertThat(test.http().get("local", "/method", String.class).body()).isEqualTo("GET");
            assertThat(test.http().post("local", "/method", "body", String.class).body())
                .isEqualTo("POST");
            assertThat(test.http().put("local", "/method", "body", String.class).body())
                .isEqualTo("PUT");
            assertThat(test.http().patch("local", "/method", "body", String.class).body())
                .isEqualTo("PATCH");
            assertThat(test.http().delete("local", "/method", String.class).body())
                .isEqualTo("DELETE");
            ExchangeResponse<List<String>> list = test.http().get(
                "local", "/list", Map.of(), HttpHeaders.EMPTY,
                new ParameterizedTypeReference<>() { });
            assertThat(list.body()).containsExactly("a", "b");
            assertThat(list.metadata().getClientName()).isEqualTo("local");
            assertThat(list.metadata().getTraceId()).isEqualTo("trace");
        }
        assertThat(events).hasSize(12);
    }

    @Test
    void retriesConfiguredStatusAndPublishesOneFinalEvent() throws Exception {
        client.getResilience().setEnabled(true);
        client.getResilience().getRetry().setWaitDuration(Duration.ZERO);
        client.getResilience().getCircuitBreaker().setEnabled(false);
        try (TestOperations test = operations(List.of())) {
            ExchangeResponse<String> response =
                test.http().get("local", "/retry", String.class);
            assertThat(response.body()).isEqualTo("recovered");
            assertThat(response.metadata().getAttemptCount()).isEqualTo(3);
            assertThat(response.metadata().getRetryCount()).isEqualTo(2);
        }
        assertThat(retries).hasValue(3);
        assertThat(events.stream().filter(event ->
            event.type().name().equals("COMPLETED"))).hasSize(1);
    }

    @Test
    void usesTypedFallbackAfterFinalFailure() throws Exception {
        try (TestOperations test = operations(List.of(new StringFallback()))) {
            ExchangeResponse<String> response =
                test.http().get("local", "/fail", String.class);
            assertThat(response.body()).isEqualTo("fallback");
            assertThat(response.metadata().isFallbackUsed()).isTrue();
        }
        assertThat(events.getLast().data().isFallbackUsed()).isTrue();
        assertThat(events.getLast().data().getErrorMessage()).doesNotContain("raw");
    }

    private TestOperations operations(List<OutboundFallbackHandler<?>> handlers) {
        ClientConfigurationResolver resolver = new ClientConfigurationResolver(properties);
        DefaultHttpClientRegistry registry =
            new DefaultHttpClientRegistry(resolver, Optional.empty());
        DefaultOutboundDataMasker masker = new DefaultOutboundDataMasker(
            new JsonMapperHelper(JsonMapper.builder().build()), java.util.Set.of(),
            java.util.Set.of());
        DefaultResilienceExecutor resilience = new DefaultResilienceExecutor(resolver);
        DefaultHttpExchangeOperations operations = new DefaultHttpExchangeOperations(
            registry, resolver, new SecureUriResolver(),
            new DefaultRetryDecisionPolicy(resolver), resilience,
            new DefaultOutboundFallbackRegistry(handlers), events::add, masker,
            new SystemTimeProvider(), new MdcRequestContextProvider(),
            () -> new CurrentTraceContext("trace", "span"));
        return new TestOperations(operations, registry);
    }

    private void method(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "text/plain", exchange.getRequestMethod());
    }

    private static void respond(
        HttpExchange exchange, int status, String contentType, String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @ExchangeFallback(client = "local")
    static final class StringFallback implements OutboundFallbackHandler<String> {
        @Override public Class<String> responseType() { return String.class; }
        @Override public boolean supports(FallbackContext context) { return true; }
        @Override public String fallback(FallbackContext context) { return "fallback"; }
    }

    private static final class TestOperations implements AutoCloseable {
        private final DefaultHttpExchangeOperations http;
        private final DefaultHttpClientRegistry registry;

        private TestOperations(
            DefaultHttpExchangeOperations http, DefaultHttpClientRegistry registry
        ) {
            this.http = http;
            this.registry = registry;
        }
        private DefaultHttpExchangeOperations http() { return http; }
        @Override public void close() throws Exception { registry.close(); }
    }
}
