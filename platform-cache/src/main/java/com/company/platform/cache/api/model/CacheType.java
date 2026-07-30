package com.company.platform.cache.api.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.Type;
import java.util.Objects;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheType<T> {
    Type type;

    private CacheType(Type type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> CacheType<T> of(Class<T> type) {
        return new CacheType<>(type);
    }
}
