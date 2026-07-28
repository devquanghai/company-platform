package com.company.platform.integration;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.logging.api.masking.DataMaskingService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationScenarioService {
    private final HttpExchangeOperations exchange;
    private final DataMaskingService masking;
    private final TimeProvider time;
    private final ObservationRegistry observationRegistry;
    private final TraceContextProvider traces;
    private final JsonMapperHelper jsonMapperHelper;

    public IntegrationScenarioResult execute(String email) {
        return Observation.createNotStarted(
                "platform.integration.scenario", observationRegistry)
            .lowCardinalityKeyValue("integration.client", "echo")
            .observe(() -> executeObserved(email));
    }

    private IntegrationScenarioResult executeObserved(String email) {
        ExchangeResponse<String> response =
            exchange.get("echo", "/echo", String.class);
        String maskedEmail = String.valueOf(
            masking.sanitizeFields(Map.of("email", email)).get("email"));
        CurrentTraceContext trace = currentTrace();

        LoggingEventBuilder event = log.atInfo()
            .addKeyValue("event.name", "PLATFORM_INTEGRATION_COMPLETED")
            .addKeyValue("upstream.status", response.statusCode())
            .addKeyValue("customer.email", maskedEmail);
        if (trace.isAvailable()) {
            event.addKeyValue("trace.id", trace.getTraceId())
                .addKeyValue("span.id", trace.getSpanId());
        }
        event.log("Core, service exchange and logging integration completed");

        return IntegrationScenarioResult.builder()
            .upstreamBody(response.body())
            .upstreamStatus(response.statusCode())
            .traceId(trace.getTraceId())
            .spanId(trace.getSpanId())
            .maskedEmail(maskedEmail)
            .timestamp(time.now())
            .build();
    }

    public IntegrationScenarioResult executePost(IntegrationRequest request) {
        log.info("Executing integration POST request: {}", jsonMapperHelper.toJson(request));
        log.info("Email: {}", request.getEmail());
        ExchangeResponse<String> response =
            exchange.post("echo-post", "/echo", request, String.class);
        return IntegrationScenarioResult.builder()
            .upstreamBody(response.body())
            .upstreamStatus(response.statusCode())
            .timestamp(time.now())
            .build();
    }

    private CurrentTraceContext currentTrace() {
        CurrentTraceContext current = traces.getCurrentContext();
        return current == null ? CurrentTraceContext.empty() : current;
    }
}
