package com.company.platform.cache.domain.result;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.OptionalLong;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class CacheClearResult {
    String strategy;
    boolean success;
    String previousToken;
    String currentToken;
    Long deletedCount;

    public OptionalLong getExactDeletedCount() {
        return deletedCount == null ? OptionalLong.empty() : OptionalLong.of(deletedCount);
    }
}
