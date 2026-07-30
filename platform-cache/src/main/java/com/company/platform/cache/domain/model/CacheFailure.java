package com.company.platform.cache.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Objects;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheFailure {
    String code;
    String category;
    boolean retryable;

    public static CacheFailure of(String code, String category, boolean retryable) {
        return new CacheFailure(
            Objects.requireNonNull(code, "code"),
            Objects.requireNonNull(category, "category"),
            retryable
        );
    }
}
