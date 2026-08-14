package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** @deprecated Configure proxy through the native HTTP client customizer. */
@Deprecated
@Getter @Setter
public class ProxyProperties {
    private boolean enabled;
    private String scheme = "http";
    private String host;
    private int port;
    private String username;
    private String password;
    private List<String> nonProxyHosts = new ArrayList<>();
    private Duration connectTimeout = Duration.ofSeconds(3);
    @Override public String toString() {
        return "ProxyProperties(enabled=" + enabled + ", scheme=" + scheme
            + ", host=" + host + ", port=" + port + ", username=***, password=***)";
    }
}
