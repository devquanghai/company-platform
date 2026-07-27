package com.company.platform.core.exception;

import com.company.platform.core.exception.error.ErrorCategory;
import java.util.Map;
import java.util.Objects;

public class PlatformException extends RuntimeException {
    private final String errorCode;
    private final ErrorCategory category;
    private final Map<String, Object> parameters;
    private final Map<String, Object> metadata;

    protected PlatformException(Builder<?> builder) {
        super(Objects.requireNonNull(builder.message, "message"), builder.cause);
        this.errorCode = Objects.requireNonNull(builder.errorCode, "errorCode");
        this.category = Objects.requireNonNull(builder.category, "category");
        this.parameters = Map.copyOf(builder.parameters);
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String errorCode() {
        return errorCode;
    }

    public ErrorCategory category() {
        return category;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder<?> builder(String errorCode, ErrorCategory category, String message) {
        return new Builder<>(errorCode, category, message);
    }

    public static class Builder<T extends Builder<T>> {
        private final String errorCode;
        private final ErrorCategory category;
        private final String message;
        private Map<String, Object> parameters = Map.of();
        private Map<String, Object> metadata = Map.of();
        private Throwable cause;

        protected Builder(String errorCode, ErrorCategory category, String message) {
            this.errorCode = errorCode;
            this.category = category;
            this.message = message;
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T parameters(Map<String, Object> parameters) {
            this.parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
            return self();
        }

        public T metadata(Map<String, Object> metadata) {
            this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
            return self();
        }

        public T cause(Throwable cause) {
            this.cause = cause;
            return self();
        }

        public PlatformException build() {
            return new PlatformException(this);
        }
    }
}
