package com.company.platform.cache.api.lock;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class LockOptions {
    @Builder.Default Duration waitTime = Duration.ofSeconds(2);
    @Builder.Default Duration leaseTime = Duration.ofSeconds(30);
    boolean fair;
    boolean watchdogEnabled;
    boolean fencingEnabled;
}
