package com.company.platform.logging.structured.customizer;

@FunctionalInterface
public interface PlatformLogEventCustomizer {
    void customize(MutablePlatformLogEvent event);
}
