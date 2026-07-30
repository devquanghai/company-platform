package com.company.platform.cache.application.port.out;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Internal result for local atomic and optimistic updates.
 */
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class BackendUpdateResult {
    Status status;
    BackendCacheEntry entry;

    public enum Status {
        UPDATED,
        VERSION_CONFLICT,
        NOT_FOUND
    }
}
