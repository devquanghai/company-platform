package com.company.platform.integration.cacheapp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CacheValueRequest(
    @NotBlank @Size(max = 4096) String value
) {
}
