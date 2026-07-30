package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisSslProperties {
    boolean enabled;
    boolean verifyPeer = true;
}
