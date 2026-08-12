package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisProperties {
    @Valid SerializationProperties serialization = new SerializationProperties();
}
