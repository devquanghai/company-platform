package com.company.platform.cache.autoconfigure.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisSentinelProperties {
    String master;
    List<String> nodes = new ArrayList<>();
    String username;
    String password;
}
