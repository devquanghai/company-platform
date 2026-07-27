package com.company.platform.core.context;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CurrentUser {
    String userId;
    String username;
    Set<String> roles = new HashSet<>();
    Set<String> permissions = new HashSet<>();
    Map<String, String> attributes = new HashMap<>();
    Map<String, Object> metaData = new HashMap<>();
}
