package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform.cache")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformCacheProperties {
    boolean enabled = true;
    boolean annotationsEnabled = true;
    @Valid CacheDefaultsProperties defaults = new CacheDefaultsProperties();
    @Valid Map<String, CacheStoreProperties> stores = new LinkedHashMap<>();
    @Valid Map<String, NamedCacheProperties> caches = new LinkedHashMap<>();
    @Valid LockProperties locking = new LockProperties();
    @Valid ObservabilityProperties observability = new ObservabilityProperties();
}
