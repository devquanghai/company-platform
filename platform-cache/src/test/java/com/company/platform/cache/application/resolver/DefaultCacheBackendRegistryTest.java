package com.company.platform.cache.application.resolver;

import com.company.platform.cache.adapter.noop.NoOpCacheBackend;
import com.company.platform.cache.domain.exception.PlatformCacheConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCacheBackendRegistryTest {

    @Test
    void normalizesLookupsAndExposesImmutableSnapshot() {
        NoOpCacheBackend backend = new NoOpCacheBackend();
        DefaultCacheBackendRegistry registry =
            new DefaultCacheBackendRegistry(Map.of("Users", backend));
        assertThat(registry.require(" USERS ")).isSameAs(backend);
        assertThat(registry.getBackends()).isSameAs(registry.snapshot());
        assertThatThrownBy(() -> registry.snapshot().put("x", backend))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnknownBlankAndNullEntries() {
        DefaultCacheBackendRegistry registry =
            new DefaultCacheBackendRegistry(Map.of("users", new NoOpCacheBackend()));
        assertThatThrownBy(() -> registry.require("secret!"))
            .isInstanceOf(PlatformCacheConfigurationException.class)
            .hasMessageContaining("secret?");
        assertThatIllegalArgumentException().isThrownBy(() -> registry.require(" "));
        assertThatNullPointerException().isThrownBy(() ->
            new DefaultCacheBackendRegistry(null));

        Map<String, com.company.platform.cache.application.port.out.CacheBackend> nullBackend =
            new LinkedHashMap<>();
        nullBackend.put("users", null);
        assertThatNullPointerException().isThrownBy(() ->
            new DefaultCacheBackendRegistry(nullBackend));

        Map<String, com.company.platform.cache.application.port.out.CacheBackend> nullName =
            new LinkedHashMap<>();
        nullName.put(null, new NoOpCacheBackend());
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DefaultCacheBackendRegistry(nullName));
    }
}
