package com.company.platform.logging.observability;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoOperation;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import com.company.platform.logging.observability.metrics.MicrometerLoggingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerLoggingMetricsTest {

    @Test
    void recordsSuccessfulAndFailedLoggingCountersWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerLoggingMetrics metrics = new MicrometerLoggingMetrics(registry);

        metrics.recordLog(LogSeverity.INFO, LogCategory.APPLICATION, true);
        metrics.recordLog(LogSeverity.ERROR, LogCategory.SECURITY, false);

        assertThat(registry.get("platform.logging.events")
            .tags("level", "INFO", "category", "APPLICATION", "outcome", "SUCCESS")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.logging.events")
            .tags("level", "ERROR", "category", "SECURITY", "outcome", "FAILED")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.logging.errors")
            .tags("level", "ERROR", "category", "SECURITY", "outcome", "FAILED")
            .counter().count()).isEqualTo(1);
        assertThat(registry.find("platform.logging.errors")
            .tags("outcome", "SUCCESS").counter()).isNull();
    }

    @Test
    void recordsMaskedAndRemovedFieldCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerLoggingMetrics metrics = new MicrometerLoggingMetrics(registry);

        metrics.recordMasking(MaskingType.PARTIAL, PiiType.EMAIL, false);
        metrics.recordMasking(MaskingType.REMOVE, PiiType.PASSWORD, true);

        assertThat(registry.get("platform.logging.masked.fields")
            .tags("masking_type", "PARTIAL", "pii_type", "EMAIL")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.logging.removed.fields")
            .tags("masking_type", "REMOVE", "pii_type", "PASSWORD")
            .counter().count()).isEqualTo(1);
    }

    @Test
    void recordsCryptoCountDurationAndFailureCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerLoggingMetrics metrics = new MicrometerLoggingMetrics(registry);

        metrics.recordCrypto(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256,
            CryptoOperation.ENCRYPT, true, Duration.ofMillis(25));
        metrics.recordCrypto(CryptoProviderType.JCA, CryptoAlgorithm.AES_GCM_256,
            CryptoOperation.DECRYPT, false, Duration.ofMillis(40));

        assertThat(registry.get("platform.crypto.operations")
            .tags("crypto_provider", "JCA", "crypto_algorithm", "AES_GCM_256",
                "operation", "ENCRYPT", "outcome", "SUCCESS")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.crypto.duration")
            .tags("crypto_provider", "JCA", "crypto_algorithm", "AES_GCM_256",
                "operation", "ENCRYPT", "outcome", "SUCCESS")
            .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isEqualTo(25);
        assertThat(registry.get("platform.crypto.errors")
            .tags("crypto_provider", "JCA", "crypto_algorithm", "AES_GCM_256",
                "operation", "DECRYPT", "outcome", "FAILED")
            .counter().count()).isEqualTo(1);
    }
}
