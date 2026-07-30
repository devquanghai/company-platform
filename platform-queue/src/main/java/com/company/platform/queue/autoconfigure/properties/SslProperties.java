package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SslProperties {
    private boolean enabled = true;
    private boolean verifyHostname = true;
    private String trustStoreLocation;
    private String trustStorePassword;
    private String keyStoreLocation;
    private String keyStorePassword;
    private String keyPassword;
}
