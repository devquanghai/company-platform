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

    /** Enables all platform service-exchange integration beans. */
    boolean enabled = true;
    /** Named outbound client definitions keyed by stable low-cardinality identity. */
    Map<String, ClientProperties> clients = new LinkedHashMap<>();

    /** @deprecated Client definitions are validated eagerly. */
    @Deprecated(forRemoval = true)
    public boolean isEagerInitialization() { return true; }
    /** @deprecated Client definitions are validated eagerly. */
    @Deprecated(forRemoval = true)
    public void setEagerInitialization(boolean ignored) { }
    /** @deprecated Insecure SSL is unsupported. */
    @Deprecated(forRemoval = true)
    public boolean isAllowInsecureSsl() { return false; }
    /** @deprecated Insecure SSL is unsupported. */
    @Deprecated(forRemoval = true)
    public void setAllowInsecureSsl(boolean ignored) { }
    /** @deprecated Use {@code spring.profiles} and native configuration. */
    @Deprecated(forRemoval = true)
    public String getEnvironment() { return "native"; }
    /** @deprecated Use {@code spring.profiles} and native configuration. */
    @Deprecated(forRemoval = true)
    public void setEnvironment(String ignored) { }
    /** @deprecated Use {@code spring.application.name}. */
    @Deprecated(forRemoval = true)
    public String getSourceApplication() { return "unknown"; }
    /** @deprecated Use {@code spring.application.name}. */
    @Deprecated(forRemoval = true)
    public void setSourceApplication(String ignored) { }
    /** @deprecated Resource lifecycle is owned by Spring Boot. */
    @Deprecated(forRemoval = true)
    public Duration getShutdownTimeout() { return Duration.ZERO; }
    /** @deprecated Resource lifecycle is owned by Spring Boot. */
    @Deprecated(forRemoval = true)
    public void setShutdownTimeout(Duration ignored) { }
    /** @deprecated Use native Boot and Resilience4j defaults. */
    @Deprecated(forRemoval = true)
    public DefaultsProperties getDefaults() { return new DefaultsProperties(); }
    /** @deprecated Use native Boot and Resilience4j defaults. */
    @Deprecated(forRemoval = true)
    public void setDefaults(DefaultsProperties ignored) { }
}
