package com.company.platform.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.exception.handler.PlatformExceptionHandler;
import com.company.platform.core.web.filter.RequestResponseLoggingFilter;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformModulesIntegrationTest {
    private static final String RAW_EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "Never-Log-This-Password";
    private static final String RAW_PHONE = "0901234567";
    private static final HttpServer UPSTREAM = startUpstream();

    @Autowired IntegrationScenarioService scenario;
    @Autowired TimeProvider timeProvider;
    @Autowired HttpExchangeOperations exchangeOperations;
    @Autowired DataMaskingService maskingService;
    @Autowired ObservationRegistry observationRegistry;
    @Autowired IntegrationScenarioController controller;
    @Value("${local.server.port}") int serverPort;

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
    void integratesCoreExchangeLoggingSlf4jAndObservability() {
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
            assertThat(observationRegistry).isNotNull();
            assertMaskingLogbackFragmentIsActive();
            assertThat(result.getUpstreamStatus()).isEqualTo(200);
            assertThat(result.getUpstreamBody()).isEqualTo("core-exchange-logging-ok");
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
                .containsEntry("customer.email", "al***@example.com");
            if (result.getTraceId() != null) {
                assertThat(fields).containsEntry("trace.id", result.getTraceId());
                assertThat(result.getSpanId()).isNotBlank();
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void appliesAnnotationMaskingAndCryptoAspects() {
        Logger logger = (Logger) LoggerFactory.getLogger(
            IntegrationScenarioController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            IntegrationRequest request = IntegrationRequest.builder()
                .email(RAW_EMAIL)
                .password(RAW_PASSWORD)
                .phoneNumber(RAW_PHONE)
                .dateOfBirth(LocalDate.of(2000, 1, 2))
                .build();

            Object sanitized = controller.maskAnnotatedRequest(request);
            assertThat(sanitized.toString())
                .contains("al***@example.com")
                .doesNotContain(RAW_EMAIL, RAW_PASSWORD, RAW_PHONE);

            String ciphertext = controller.encryptResult("enterprise-secret");
            assertThat(ciphertext)
                .isNotBlank()
                .doesNotContain("enterprise-secret");
            assertThat(controller.decryptArgument(ciphertext))
                .containsEntry("plaintext", "enterprise-secret");

            assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(RAW_EMAIL)
                    || message.contains(RAW_PASSWORD)
                    || message.contains(RAW_PHONE));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void exposesLocalizedValidationResponseAndRequestCompletionLog() throws Exception {
        Logger exceptionLogger = (Logger) LoggerFactory.getLogger(
            PlatformExceptionHandler.class);
        Logger requestLogger = (Logger) LoggerFactory.getLogger(
            RequestResponseLoggingFilter.class);
        ListAppender<ILoggingEvent> exceptionAppender = listAppender(exceptionLogger);
        ListAppender<ILoggingEvent> requestAppender = listAppender(requestLogger);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                    + "/platform/integration"))
                .header("Content-Type", "application/json")
                .header("Accept-Language", "vi")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"email":"   ","password":"Never-Log-This-Password"}
                    """))
                .build();

            HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body())
                .contains("\"success\":false")
                .contains("error.validation.field-required")
                .contains("Trường email là bắt buộc.")
                .doesNotContain("Never-Log-This-Password");
            assertThat(exceptionAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.startsWith(
                    "Request validation failed: method=POST, "
                        + "path=/platform/integration"));
            assertThat(requestAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.startsWith(
                    "HTTP POST /platform/integration completed status=400"));

            HttpRequest jacksonRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort
                    + "/platform/integration"))
                .header("Content-Type", "application/json")
                .header("Accept-Language", "vi")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                      "email":"alice@example.com",
                      "dateOfBirth":"29/07/2026"
                    }
                    """))
                .build();
            HttpResponse<String> jacksonResponse = client.send(
                jacksonRequest, HttpResponse.BodyHandlers.ofString());

            assertThat(jacksonResponse.statusCode()).isEqualTo(400);
            assertThat(jacksonResponse.body())
                .contains("\"field\":\"dateOfBirth\"")
                .contains("\"code\":\"error.validation.field-date\"")
                .contains("Trường dateOfBirth phải là ngày hợp lệ.")
                .doesNotContain("DateTimeParseException");
        } finally {
            detach(exceptionLogger, exceptionAppender);
            detach(requestLogger, requestAppender);
        }
    }

    private static ListAppender<ILoggingEvent> listAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(
        Logger logger, ListAppender<ILoggingEvent> appender
    ) {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static void assertMaskingLogbackFragmentIsActive() {
        Logger root = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        ConsoleAppender<?> console = (ConsoleAppender<?>)
            root.getAppender("PLATFORM_CONSOLE_TEXT");
        assertThat(console).isNotNull();
        assertThat(console.getEncoder()).isInstanceOf(PatternLayoutEncoder.class);
        PatternLayoutEncoder encoder = (PatternLayoutEncoder) console.getEncoder();
        assertThat(encoder.getPattern())
            .contains("%maskedMsg", "%maskedKv", "%maskedMdc", "%safeEx");
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
