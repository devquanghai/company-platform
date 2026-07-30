package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResilienceProperties {
    boolean enabled = true;
    @Valid RetryProperties retry = new RetryProperties();
    @Valid CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    @Valid BulkheadProperties bulkhead = new BulkheadProperties();
}
