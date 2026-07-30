package com.company.platform.cache.autoconfigure.properties;

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
public class SerializationProperties {
    String key = "STRING";
    String value = "JSON";
    boolean valueEnvelopeEnabled = true;
    @Min(1) int schemaVersion = 1;
    String schemaId = "platform-cache";
    List<String> trustedPackages = new ArrayList<>();
}
