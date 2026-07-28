package com.company.platform.logging.logback.converter;

import com.company.platform.logging.api.masking.DataMaskingService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridges the Spring-managed masking policy to Logback converters.
 *
 * <p>Logback is created before the application context, so converters first use
 * the immutable bootstrap deny-list. Once Spring has bound application
 * properties, this lifecycle installs the configured {@link DataMaskingService}.
 * Closing an older context cannot clear a newer context's policy.</p>
 */
public final class LogbackMaskingLifecycle
    implements InitializingBean, DisposableBean {

    private static final AtomicReference<DataMaskingService> ACTIVE =
        new AtomicReference<>();

    private final DataMaskingService masking;

    public LogbackMaskingLifecycle(DataMaskingService masking) {
        this.masking = Objects.requireNonNull(masking, "masking must not be null");
    }

    @Override
    public void afterPropertiesSet() {
        ACTIVE.set(masking);
    }

    @Override
    public void destroy() {
        ACTIVE.compareAndSet(masking, null);
    }

    static String sanitizeMessage(String value) {
        DataMaskingService current = ACTIVE.get();
        return current == null ? value : current.sanitizeMessage(value);
    }

    static String sanitizeValue(String key, String value) {
        DataMaskingService current = ACTIVE.get();
        return current == null ? value : current.maskValue(key, value);
    }

    static boolean isActive() {
        return ACTIVE.get() != null;
    }
}
