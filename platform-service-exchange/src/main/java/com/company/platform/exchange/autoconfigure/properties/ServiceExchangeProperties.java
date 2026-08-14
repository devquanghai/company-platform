package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform.service-exchange")
@FieldDefaults(level = PRIVATE)
public class ServiceExchangeProperties {

    /**
     * Enables all platform service-exchange integration beans.
     */
    boolean enabled = true;
    /**
     * Named outbound client definitions keyed by stable low-cardinality identity.
     */
    Map<String, ClientProperties> clients = new LinkedHashMap<>();
}
