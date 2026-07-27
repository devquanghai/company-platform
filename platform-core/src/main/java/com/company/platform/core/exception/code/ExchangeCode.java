package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ExchangeCode implements I18nKey {

    FAILED("error.exchange.failed"),
    CLIENT_ERROR("error.exchange.client-error"),
    SERVER_ERROR("error.exchange.server-error"),
    TIMEOUT("error.exchange.timeout"),
    CONNECTION_FAILED("error.exchange.connection-failed"),
    CONNECTION_REFUSED("error.exchange.connection-refused"),
    INVALID_RESPONSE("error.exchange.invalid-response"),
    EMPTY_RESPONSE("error.exchange.empty-response"),
    UNEXPECTED_STATUS("error.exchange.unexpected-status"),
    SERIALIZATION_FAILED("error.exchange.serialization-failed"),
    DESERIALIZATION_FAILED("error.exchange.deserialization-failed"),
    AUTHENTICATION_FAILED("error.exchange.authentication-failed"),
    SERVICE_UNAVAILABLE("error.exchange.service-unavailable"),
    CIRCUIT_OPEN("error.exchange.circuit-open"),
    RETRY_EXHAUSTED("error.exchange.retry-exhausted"),
    FALLBACK_FAILED("error.exchange.fallback-failed");

    String key;

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum CacheCode implements I18nKey {

        OPERATION_FAILED("error.cache.operation-failed"),
        READ_FAILED("error.cache.read-failed"),
        WRITE_FAILED("error.cache.write-failed"),
        EVICT_FAILED("error.cache.evict-failed"),
        CLEAR_FAILED("error.cache.clear-failed"),
        CONNECTION_FAILED("error.cache.connection-failed"),
        LOCK_FAILED("error.cache.lock-failed"),
        LOCK_TIMEOUT("error.cache.lock-timeout"),
        KEY_NOT_FOUND("error.cache.key-not-found");

        String key;
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum ConcurrentCode implements I18nKey {

        MODIFICATION("error.concurrent.modification"),
        LOCKED("error.concurrent.locked"),
        OPERATION_IN_PROGRESS("error.concurrent.operation-in-progress"),
        DUPLICATE_REQUEST("error.concurrent.duplicate-request"),
        VERSION_CONFLICT("error.concurrent.version-conflict"),
        LOCK_TIMEOUT("error.concurrent.lock-timeout"),
        RETRY("error.concurrent.retry");

        String key;
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum ConfigurationCode implements I18nKey {

        INVALID("error.config.invalid"),
        MISSING("error.config.missing"),
        INVALID_VALUE("error.config.invalid-value"),
        LOAD_FAILED("error.config.load-failed"),
        REFRESH_FAILED("error.config.refresh-failed"),
        FEATURE_DISABLED("error.config.feature-disabled");

        String key;
    }

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum DatabaseCode implements I18nKey {

        OPERATION_FAILED("error.database.operation-failed"),
        CONNECTION_FAILED("error.database.connection-failed"),
        QUERY_FAILED("error.database.query-failed"),
        INSERT_FAILED("error.database.insert-failed"),
        UPDATE_FAILED("error.database.update-failed"),
        DELETE_FAILED("error.database.delete-failed"),
        CONSTRAINT_VIOLATION("error.database.constraint-violation"),
        DUPLICATE_KEY("error.database.duplicate-key"),
        FOREIGN_KEY_VIOLATION("error.database.foreign-key-violation"),
        OPTIMISTIC_LOCK("error.database.optimistic-lock"),
        PESSIMISTIC_LOCK("error.database.pessimistic-lock"),
        TRANSACTION_FAILED("error.database.transaction-failed"),
        ROLLBACK_FAILED("error.database.rollback-failed");

        String key;
    }
}
