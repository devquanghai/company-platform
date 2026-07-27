package com.company.platform.core.json;

import com.company.platform.core.exception.PlatformInfrastructureException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Injectable, application-configured Jackson 3 operations for JSON serialization,
 * deserialization, tree access, collection conversion, and object updates.
 *
 * <p>The helper never creates a private mapper, never logs payloads, and normalizes
 * mapping failures to stable platform infrastructure error codes.</p>
 */
public final class JsonMapperHelper {

    private static final String ERROR_CODE_PREFIX = "CORE.JSON.";
    private final JsonMapper jsonMapper;

    public JsonMapperHelper(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /** Returns the Boot-managed mapper used by every operation. */
    public JsonMapper getJsonMapper() { return jsonMapper; }

    public String toJson(Object value) {
        try { return jsonMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw failure("SERIALIZE", "JSON serialization failed", exception);
        }
    }

    public byte[] toBytes(Object value) {
        try { return jsonMapper.writeValueAsBytes(value); }
        catch (JacksonException exception) {
            throw failure("SERIALIZE", "JSON byte serialization failed", exception);
        }
    }

    public <T> T fromJson(String json, Class<T> targetType) {
        require(json, targetType);
        try { return jsonMapper.readValue(json, targetType); }
        catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON deserialization failed", exception);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> targetType) {
        require(json, targetType);
        try { return jsonMapper.readValue(json, targetType); }
        catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON deserialization failed", exception);
        }
    }

    public <T> T fromBytes(byte[] json, Class<T> targetType) {
        require(json, targetType);
        try { return jsonMapper.readValue(json, targetType); }
        catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON byte deserialization failed", exception);
        }
    }

    public <T> T fromBytes(byte[] json, TypeReference<T> targetType) {
        require(json, targetType);
        try { return jsonMapper.readValue(json, targetType); }
        catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON byte deserialization failed", exception);
        }
    }

    public JsonNode readTree(String json) {
        Objects.requireNonNull(json, "json must not be null");
        try { return jsonMapper.readTree(json); }
        catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON tree parsing failed", exception);
        }
    }

    public String prettyPrint(String json) {
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(readTree(json));
    }

    public Map<String, Object> toMap(Object value) {
        Objects.requireNonNull(value, "value must not be null");
        return value instanceof String json
            ? fromJson(json, new TypeReference<Map<String, Object>>() { })
            : convert(value, new TypeReference<Map<String, Object>>() { });
    }

    public <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyType, Class<V> valueType) {
        require(json, keyType);
        Objects.requireNonNull(valueType, "valueType must not be null");
        try {
            return jsonMapper.readValue(
                json,
                jsonMapper.getTypeFactory().constructMapType(Map.class, keyType, valueType)
            );
        } catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON map deserialization failed", exception);
        }
    }

    public <T> List<T> fromJsonToList(String json, Class<T> elementType) {
        require(json, elementType);
        try {
            return jsonMapper.readValue(
                json,
                jsonMapper.getTypeFactory().constructCollectionType(List.class, elementType)
            );
        } catch (JacksonException exception) {
            throw failure("DESERIALIZE", "JSON list deserialization failed", exception);
        }
    }

    public <T> T fromMap(Map<String, ?> values, Class<T> targetType) {
        Objects.requireNonNull(values, "values must not be null");
        return convert(values, targetType);
    }

    public <T> T convert(Object value, Class<T> targetType) {
        require(value, targetType);
        try { return jsonMapper.convertValue(value, targetType); }
        catch (JacksonException | IllegalArgumentException exception) {
            throw failure("CONVERT", "JSON-compatible value conversion failed", exception);
        }
    }

    public <T> T convert(Object value, TypeReference<T> targetType) {
        require(value, targetType);
        try { return jsonMapper.convertValue(value, targetType); }
        catch (JacksonException | IllegalArgumentException exception) {
            throw failure("CONVERT", "JSON-compatible value conversion failed", exception);
        }
    }

    public <T> T merge(String json, T target) {
        require(json, target);
        try { return jsonMapper.readerForUpdating(target).readValue(json); }
        catch (JacksonException exception) {
            throw failure("MERGE", "JSON merge failed", exception);
        }
    }

    private static void require(Object value, Object targetType) {
        Objects.requireNonNull(value, "source must not be null");
        Objects.requireNonNull(targetType, "target must not be null");
    }

    private static PlatformInfrastructureException failure(
        String operation,
        String message,
        Throwable cause
    ) {
        return new PlatformInfrastructureException(ERROR_CODE_PREFIX + operation, message, cause);
    }
}
