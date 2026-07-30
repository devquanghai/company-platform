package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.web")
@Validated
public class PlatformWebProperties {

    /** Bật các quy ước Spring MVC chuẩn của platform. */
    boolean enabled = true;
    boolean requestLoggingEnabled;
}
