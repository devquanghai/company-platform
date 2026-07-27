package com.company.platform.logging.observability.metrics;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoOperation;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;

import java.time.Duration;

public interface LoggingMetrics {
    void recordLog(LogSeverity level, LogCategory category, boolean success);
    void recordMasking(MaskingType type, PiiType piiType, boolean removed);
    void recordCrypto(
        CryptoProviderType provider, CryptoAlgorithm algorithm,
        CryptoOperation operation, boolean success, Duration duration
    );
}
