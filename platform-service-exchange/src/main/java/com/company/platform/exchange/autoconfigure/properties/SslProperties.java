package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import java.util.LinkedHashSet;
import java.util.Set;

/** @deprecated Configure {@code spring.ssl.bundle.*}. */
@Deprecated
@Getter @Setter
public class SslProperties {
    private boolean enabled;
    private String bundle;
    private boolean hostnameVerificationEnabled = true;
    private boolean trustAll;
    private Set<String> protocols = new LinkedHashSet<>();
    private Set<String> cipherSuites = new LinkedHashSet<>();
}
