package com.company.platform.cache.domain.result;

import com.company.platform.cache.domain.model.CacheFailure;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class AtomicUpdateResult<T> {
    boolean updated;
    T value;
    CacheFailure failure;
}
