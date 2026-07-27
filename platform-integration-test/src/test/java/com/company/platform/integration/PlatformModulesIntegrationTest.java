package com.company.platform.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlatformModulesIntegrationTest {
    private static final String RAW_EMAIL = "alice@example.com";
    private static final HttpServer UPSTREAM = startUpstream();

    @Autowired IntegrationScenarioService scenario;
    @Autowired TimeProvider timeProvider;
    @Autowired HttpExchangeOperations exchangeOperations;
    @Autowired DataMaskingService maskingService;
    @Autowired OpenTelemetry openTelemetry;

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.service-exchange.clients.echo.http.base-url",
            () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void integratesCoreExchangeLoggingSlf4jAndOpenTelemetry() {
        Logger logger = (Logger) LoggerFactory.getLogger(
            IntegrationScenarioService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            IntegrationScenarioResult result = scenario.execute(RAW_EMAIL);

            assertThat(timeProvider).isNotNull();
            assertThat(exchangeOperations).isNotNull();
            assertThat(maskingService).isNotNull();
            assertThat(openTelemetry).isNotNull();
            assertThat(result.getUpstreamStatus()).isEqualTo(200);
            assertThat(result.getUpstreamBody()).isEqualTo("core-exchange-logging-ok");
            assertThat(result.getTraceId()).hasSize(32).doesNotMatch("0+");
            assertThat(result.getSpanId()).hasSize(16).doesNotMatch("0+");
            assertThat(result.getMaskedEmail())
                .isEqualTo("al***@example.com")
                .doesNotContain(RAW_EMAIL);
            assertThat(result.getTimestamp()).isNotNull();

            ILoggingEvent event = appender.list.getLast();
            Map<String, String> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(
                    pair -> pair.key,
                    pair -> String.valueOf(pair.value)));
            assertThat(event.getFormattedMessage())
                .isEqualTo("Core, service exchange and logging integration completed")
                .doesNotContain(RAW_EMAIL);
            assertThat(fields)
                .containsEntry("event.name", "PLATFORM_INTEGRATION_COMPLETED")
                .containsEntry("customer.email", "al***@example.com")
                .containsEntry("trace.id", result.getTraceId());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/echo", PlatformModulesIntegrationTest::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        byte[] body = "core-exchange-logging-ok".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
