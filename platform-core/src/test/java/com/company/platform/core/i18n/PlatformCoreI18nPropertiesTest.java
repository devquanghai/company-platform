package com.company.platform.core.i18n;

import com.company.platform.core.configuration.properties.PlatformCoreI18nProperties;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCoreI18nPropertiesTest {

    @Test
    void exposesStableDefaultsAndDefensiveBasenameCopies() {
        PlatformCoreI18nProperties properties = new PlatformCoreI18nProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBasenames()).containsExactly("core_message", "messages");
        assertThat(properties.getDefaultLocale()).isEqualTo("en");
        assertThat(properties.isFallbackToSystemLocale()).isFalse();
        assertThat(properties.isUseCodeAsDefaultMessage()).isTrue();
        assertThat(properties.getCacheDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getEncoding()).isEqualTo("UTF-8");

        List<String> names = new java.util.ArrayList<>(List.of("messages"));
        properties.setBasenames(names);
        names.add("mutated");
        assertThat(properties.getBasenames()).containsExactly("messages");
        properties.setBasenames(null);
        assertThat(properties.getBasenames()).isEmpty();
    }

    @Test
    void supportsAllConfigurationSetters() {
        PlatformCoreI18nProperties properties = new PlatformCoreI18nProperties();
        properties.setEnabled(false);
        properties.setDefaultLocale("vi-VN");
        properties.setFallbackToSystemLocale(true);
        properties.setUseCodeAsDefaultMessage(false);
        properties.setCacheDuration(Duration.ZERO);
        properties.setEncoding("US-ASCII");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultLocale()).isEqualTo("vi-VN");
        assertThat(properties.isFallbackToSystemLocale()).isTrue();
        assertThat(properties.isUseCodeAsDefaultMessage()).isFalse();
        assertThat(properties.getCacheDuration()).isZero();
        assertThat(properties.getEncoding()).isEqualTo("US-ASCII");
    }
}
