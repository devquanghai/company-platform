package com.company.platform.cache.application.port.out;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Internal clear result. Distributed backends may not know the deleted count.
 */
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class BackendClearResult {
    String strategy;
    String previousNamespaceToken;
    String currentNamespaceToken;
    Long exactDeletedCount;
}
