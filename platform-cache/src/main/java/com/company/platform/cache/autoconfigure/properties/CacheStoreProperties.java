package com.company.platform.cache.autoconfigure.properties;

import com.company.platform.cache.domain.model.CacheProviderType;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheStoreProperties {
    boolean enabled = true;
    CacheProviderType provider = CacheProviderType.CAFFEINE;
    @Valid CaffeineProperties caffeine = new CaffeineProperties();
    @Valid RedisProperties redis = new RedisProperties();
    String connectionFactoryBean;
}
