package com.company.platform.cache.observability.metrics;

import com.company.platform.cache.observability.event.CacheOperationEvent;

public interface CacheMetricsRecorder {
    void record(CacheOperationEvent event);
}
