package com.company.platform.cache.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisClusterProperties {
    List<String> nodes = new ArrayList<>();
    @Min(1) int maxRedirects = 5;
    @Valid TopologyRefreshProperties topologyRefresh = new TopologyRefreshProperties();
}
