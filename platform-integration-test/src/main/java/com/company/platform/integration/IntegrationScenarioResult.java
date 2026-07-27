package com.company.platform.integration;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntegrationScenarioResult {
    String upstreamBody;
    int upstreamStatus;
    String traceId;
    String spanId;
    String maskedEmail;
    OffsetDateTime timestamp;
}
