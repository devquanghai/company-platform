package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheKeyProperties {
    String prefix;
    String version = "v1";
    String hashTag;
    boolean sensitive;
}
