package com.company.platform.logging.domain.model;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class KeyVersion {
    private final String value;
    private final boolean active;

    public KeyVersion(String value, boolean active) {
        this.value = Objects.requireNonNull(value, "value");
        this.active = active;
    }

    @Override public String toString() {
        return "KeyVersion(value=<redacted>, active=" + active + ")";
    }
}
