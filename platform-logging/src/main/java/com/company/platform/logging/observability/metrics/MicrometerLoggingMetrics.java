package com.company.platform.logging.observability.metrics;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoOperation;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;

public final class MicrometerLoggingMetrics implements LoggingMetrics {
    private final MeterRegistry registry;

    public MicrometerLoggingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordLog(LogSeverity level, LogCategory category, boolean success) {
        Tags tags = Tags.of("level", level.name(), "category", category.name(),
            "outcome", success ? "SUCCESS" : "FAILED");
        registry.counter("platform.logging.events", tags).increment();
        if (!success) {
            registry.counter("platform.logging.errors", tags).increment();
        }
    }

    @Override
    public void recordMasking(MaskingType type, PiiType piiType, boolean removed) {
        Tags tags = Tags.of("masking_type", type.name(), "pii_type", piiType.name());
        registry.counter(removed ? "platform.logging.removed.fields"
            : "platform.logging.masked.fields", tags).increment();
    }

    @Override
    public void recordCrypto(
        CryptoProviderType provider, CryptoAlgorithm algorithm,
        CryptoOperation operation, boolean success, Duration duration
    ) {
        Tags tags = Tags.of("crypto_provider", provider.name(),
            "crypto_algorithm", algorithm.name(), "operation", operation.name(),
            "outcome", success ? "SUCCESS" : "FAILED");
        registry.counter("platform.crypto.operations", tags).increment();
        registry.timer("platform.crypto.duration", tags).record(duration);
        if (!success) {
            registry.counter("platform.crypto.errors", tags).increment();
        }
    }
}
