package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.LinkedHashSet;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class SslProperties {

    boolean enabled;
    String bundle;
    boolean hostnameVerificationEnabled = true;
    boolean trustAll;
    Set<String> protocols = new LinkedHashSet<>();
    Set<String> cipherSuites = new LinkedHashSet<>();
}
