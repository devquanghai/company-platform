package com.company.platform.exchange.domain.model;

import lombok.Builder;
import lombok.Getter;

/** @deprecated Configure proxy through native client customization. */
@Deprecated
@Getter @Builder
public final class ProxyEndpoint {
    private final String scheme;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    @Override public String toString() {
        return "ProxyEndpoint(scheme=" + scheme + ", host=" + host
            + ", port=" + port + ", username=***, password=***)";
    }
}
