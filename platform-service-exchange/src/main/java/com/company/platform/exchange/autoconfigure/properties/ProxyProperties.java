package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class ProxyProperties {

    boolean enabled;
    String scheme = "http";
    String host;
    int port;
    String username;
    String password;
    List<String> nonProxyHosts = new ArrayList<>();
    Duration connectTimeout = Duration.ofSeconds(3);

    @Override
    public String toString() {
        return "ProxyProperties(enabled=" + enabled + ", scheme=" + scheme
            + ", host=" + host + ", port=" + port + ", username=***, password=***)";
    }
}
