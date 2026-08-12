package com.company.platform.cache.autoconfigure.properties;

import com.company.platform.cache.domain.policy.CacheFallbackMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FallbackProperties {
    boolean enabled;
    CacheFallbackMode mode = CacheFallbackMode.NONE;
    String localStore;
    Duration ttl = Duration.ofMinutes(2);
    Duration maximumStale = Duration.ofMinutes(5);
    boolean clearOnPrimaryRecovery = true;
    boolean allowLocalWriteFallback;
}
