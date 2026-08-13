package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform.cache")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformCacheProperties {
    boolean enabled = true;
    Provider provider = Provider.CAFFEINE;

    public enum Provider {
        REDIS,
        CAFFEINE
    }
}
