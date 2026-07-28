package com.company.platform.integration;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.logging.api.masking.DataMaskingService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationScenarioService {
    private static final String INSTRUMENTATION_NAME =
        "com.company.platform.integration";

    private final HttpExchangeOperations exchange;
    private final DataMaskingService masking;
    private final TimeProvider time;
    private final OpenTelemetry openTelemetry;

    public IntegrationScenarioResult execute(String email) {
        Tracer tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        Span span = tracer.spanBuilder("platform.integration.scenario").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ExchangeResponse<String> response =
                exchange.get("echo", "/echo", String.class);
            String maskedEmail = String.valueOf(
                masking.sanitizeFields(Map.of("email", email)).get("email"));

            log.atInfo()
                .addKeyValue("event.name", "PLATFORM_INTEGRATION_COMPLETED")
                .addKeyValue("upstream.status", response.statusCode())
                .addKeyValue("customer.email", maskedEmail)
                .addKeyValue("trace.id", span.getSpanContext().getTraceId())
                .log("Core, service exchange and logging integration completed");

            return IntegrationScenarioResult.builder()
                .upstreamBody(response.body())
                .upstreamStatus(response.statusCode())
                .traceId(span.getSpanContext().getTraceId())
                .spanId(span.getSpanContext().getSpanId())
                .maskedEmail(maskedEmail)
                .timestamp(time.now())
                .build();
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    public IntegrationScenarioResult executePost(IntegrationRequest request) {
        log.info("Executing POST request: {}", JsonMapperHelper.toJson(request));
        ExchangeResponse<String> response =
            exchange.post("echo-post", "/echo", request, String.class);
        return IntegrationScenarioResult.builder()
            .upstreamBody(response.body())
            .upstreamStatus(response.statusCode())
            .timestamp(time.now())
            .build();
    }
}
