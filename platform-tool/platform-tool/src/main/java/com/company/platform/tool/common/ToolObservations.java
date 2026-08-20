package com.company.platform.tool.common;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

public final class ToolObservations {
    private ToolObservations() {
    }

    public static void observe(String name, String type, ObservationRegistry registry, Runnable operation) {
        observe(name, type, registry, () -> {
            operation.run();
            return null;
        });
    }

    public static <T> T observe(String name, String type, ObservationRegistry registry, Supplier<T> operation) {
        Observation observation = Observation.start(name, registry).lowCardinalityKeyValue("operation", name).lowCardinalityKeyValue("type", type);
        try (Observation.Scope ignored = observation.openScope()) {
            T value = operation.get();
            observation.lowCardinalityKeyValue("result", "success");
            return value;
        } catch (RuntimeException exception) {
            observation.lowCardinalityKeyValue("result", "error").error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }
}
