package com.company.platform.logging.structured.event;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class PlatformLogField {
    private final String name;
    private final Object value;

    public PlatformLogField(String name, Object value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
    }
}
