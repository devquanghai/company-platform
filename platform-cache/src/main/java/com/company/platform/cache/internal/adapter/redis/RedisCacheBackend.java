package com.company.platform.cache.internal.adapter.redis;

import com.company.platform.cache.internal.application.port.out.BackendCacheEntry;
import com.company.platform.cache.internal.application.port.out.BackendClearResult;
import com.company.platform.cache.internal.application.port.out.BackendUpdateResult;
import com.company.platform.cache.internal.application.port.out.CacheBackend;
import com.company.platform.core.json.JsonMapperHelper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public final class RedisCacheBackend implements CacheBackend {
    private static final byte[] PAYLOAD = bytes("payload");
    private static final byte[] VERSION = bytes("version");
    private static final byte[] SCHEMA_ID = bytes("schemaId");
    private static final byte[] SCHEMA_VERSION = bytes("schemaVersion");
    private static final int OPTIMISTIC_ATTEMPTS = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> PUT_SCRIPT = script("""
        redis.call('HINCRBY', KEYS[1], 'version', 1)
        redis.call('HSET', KEYS[1], 'payload', ARGV[1],
          'schemaId', ARGV[3], 'schemaVersion', ARGV[4])
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        return 1
        """);
    private static final DefaultRedisScript<Long> PUT_IF_ABSENT_SCRIPT = script("""
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call('HSET', KEYS[1], 'payload', ARGV[1], 'version', '1',
          'schemaId', ARGV[3], 'schemaVersion', ARGV[4])
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        return 1
        """);
    private static final DefaultRedisScript<Long> CAS_SCRIPT = script("""
        local current = redis.call('HGET', KEYS[1], 'payload')
        if not current then return 0 end
        if redis.call('HGET', KEYS[1], 'schemaId') ~= ARGV[3]
          or redis.call('HGET', KEYS[1], 'schemaVersion') ~= ARGV[4] then return -1 end
        if current ~= ARGV[1] then return 0 end
        redis.call('HSET', KEYS[1], 'payload', ARGV[2])
        redis.call('HINCRBY', KEYS[1], 'version', 1)
        return 1
        """);
    private static final DefaultRedisScript<Long> COMPARE_DELETE_SCRIPT = script("""
        local current = redis.call('HGET', KEYS[1], 'payload')
        if not current then return 0 end
        if redis.call('HGET', KEYS[1], 'schemaId') ~= ARGV[2]
          or redis.call('HGET', KEYS[1], 'schemaVersion') ~= ARGV[3] then return -1 end
        if current ~= ARGV[1] then return 0 end
        return redis.call('DEL', KEYS[1])
        """);
    private static final DefaultRedisScript<Long> VERSIONED_UPDATE_SCRIPT = script("""
        local version = redis.call('HGET', KEYS[1], 'version')
        if not version then return -1 end
        if redis.call('HGET', KEYS[1], 'schemaId') ~= ARGV[4]
          or redis.call('HGET', KEYS[1], 'schemaVersion') ~= ARGV[5] then return -2 end
        if version ~= ARGV[1] then return 0 end
        redis.call('HSET', KEYS[1], 'payload', ARGV[2], 'version', ARGV[3])
        return 1
        """);
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = script("""
        local current = redis.call('HGET', KEYS[1], 'payload')
        if not current then
          redis.call('HSET', KEYS[1], 'payload', '0', 'version', '0',
            'schemaId', ARGV[3], 'schemaVersion', ARGV[4])
          redis.call('PEXPIRE', KEYS[1], ARGV[2])
        end
        if redis.call('HGET', KEYS[1], 'schemaId') ~= ARGV[3]
          or redis.call('HGET', KEYS[1], 'schemaVersion') ~= ARGV[4] then
          return redis.error_reply('CACHE_SCHEMA_MISMATCH')
        end
        redis.call('HINCRBY', KEYS[1], 'payload', ARGV[1])
        redis.call('HINCRBY', KEYS[1], 'version', 1)
        return redis.call('HGET', KEYS[1], 'payload')
        """);

    private final RedisTemplate<String, byte[]> template;
    private final HashOperations<String, byte[], byte[]> hashes;
    private final JsonMapperHelper json;
    private final String namespaceKey;
    private final byte[] schemaId;
    private final byte[] schemaVersion;

    public RedisCacheBackend(
        RedisTemplate<String, byte[]> template,
        JsonMapperHelper json,
        String keyPrefix,
        String cacheName
    ) {
        this(template, json, keyPrefix, cacheName, "platform-cache", 1);
    }

    public RedisCacheBackend(
        RedisTemplate<String, byte[]> template,
        JsonMapperHelper json,
        String keyPrefix,
        String cacheName,
        String schemaId,
        int schemaVersion
    ) {
        this.template = Objects.requireNonNull(template, "template");
        this.hashes = template.opsForHash();
        this.json = Objects.requireNonNull(json, "json");
        this.namespaceKey = requirePrefix(keyPrefix) + ":{" + requireName(cacheName)
            + "}:" + cacheName + ":namespace";
        this.schemaId = bytes(requireSchemaId(schemaId));
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = bytes(Integer.toString(schemaVersion));
    }

    @Override
    public Optional<BackendCacheEntry> get(String key) {
        requireKey(key);
        List<byte[]> values = hashes.multiGet(
            key, List.of(PAYLOAD, VERSION, SCHEMA_ID, SCHEMA_VERSION));
        if (values.size() < 4 || values.get(0) == null || values.get(1) == null) {
            return Optional.empty();
        }
        if (!java.util.Arrays.equals(schemaId, values.get(2))
            || !java.util.Arrays.equals(schemaVersion, values.get(3))) {
            throw new IllegalStateException("Redis cache value schema is incompatible");
        }
        long version = Long.parseLong(text(values.get(1)));
        Long ttlMillis = template.getExpire(key, TimeUnit.MILLISECONDS);
        Duration ttl = ttlMillis == null || ttlMillis < 0
            ? Duration.ZERO : Duration.ofMillis(ttlMillis);
        return Optional.of(new BackendCacheEntry(
            json.fromBytes(values.get(0), Object.class), version, ttl));
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        execute(PUT_SCRIPT, key, json.toBytes(requireValue(value)), millis(ttl),
            schemaId, schemaVersion);
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        return execute(
            PUT_IF_ABSENT_SCRIPT, key, json.toBytes(requireValue(value)), millis(ttl),
            schemaId, schemaVersion) == 1L;
    }

    @Override
    public boolean evict(String key) {
        requireKey(key);
        return Boolean.TRUE.equals(template.delete(key));
    }

    @Override
    public BackendClearResult clear() {
        byte[] next = bytes(newNamespaceToken());
        byte[] previous = template.opsForValue().getAndSet(namespaceKey, next);
        return new BackendClearResult(
            "REDIS_NAMESPACE_ROTATION",
            previous == null ? null : text(previous),
            text(next),
            null);
    }

    @Override
    public String namespaceToken() {
        byte[] token = template.opsForValue().get(namespaceKey);
        if (token == null) {
            initializeNamespace();
            token = template.opsForValue().get(namespaceKey);
        }
        if (token == null) {
            throw new IllegalStateException("Redis namespace token is unavailable");
        }
        return text(token);
    }

    @Override
    public long estimatedSize() {
        return -1L;
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        return execute(INCREMENT_SCRIPT, key, bytes(Long.toString(delta)), millis(ttl),
            schemaId, schemaVersion);
    }

    @Override
    public boolean compareAndSet(
        String key, Object expectedValue, Object newValue
    ) {
        long result = execute(CAS_SCRIPT, key,
            json.toBytes(expectedValue), json.toBytes(requireValue(newValue)),
            schemaId, schemaVersion);
        requireCompatible(result);
        return result == 1L;
    }

    @Override
    public boolean compareAndDelete(String key, Object expectedValue) {
        long result = execute(
            COMPARE_DELETE_SCRIPT, key, json.toBytes(expectedValue),
            schemaId, schemaVersion);
        requireCompatible(result);
        return result == 1L;
    }

    @Override
    public BackendUpdateResult updateIfVersion(
        String key, long expectedVersion, Object newValue
    ) {
        long nextVersion = Math.addExact(expectedVersion, 1L);
        long outcome = execute(VERSIONED_UPDATE_SCRIPT, key,
            bytes(Long.toString(expectedVersion)),
            json.toBytes(requireValue(newValue)),
            bytes(Long.toString(nextVersion)),
            schemaId, schemaVersion);
        if (outcome == -2L) {
            throw new IllegalStateException("Redis cache value schema is incompatible");
        }
        if (outcome < 0) {
            return new BackendUpdateResult(BackendUpdateResult.Status.NOT_FOUND, null);
        }
        if (outcome == 0) {
            return new BackendUpdateResult(
                BackendUpdateResult.Status.VERSION_CONFLICT,
                get(key).orElse(null));
        }
        return new BackendUpdateResult(
            BackendUpdateResult.Status.UPDATED, get(key).orElse(null));
    }

    @Override
    public BackendUpdateResult compute(
        String key, UnaryOperator<Object> updater
    ) {
        Objects.requireNonNull(updater, "updater");
        for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
            Optional<BackendCacheEntry> current = get(key);
            if (current.isEmpty()) {
                return new BackendUpdateResult(
                    BackendUpdateResult.Status.NOT_FOUND, null);
            }
            Object updated = Objects.requireNonNull(
                updater.apply(current.get().getValue()), "updater result");
            BackendUpdateResult result = updateIfVersion(
                key, current.get().getVersion(), updated);
            if (result.getStatus() != BackendUpdateResult.Status.VERSION_CONFLICT) {
                return result;
            }
        }
        return new BackendUpdateResult(
            BackendUpdateResult.Status.VERSION_CONFLICT, get(key).orElse(null));
    }

    private long execute(
        DefaultRedisScript<Long> script, String key, byte[]... arguments
    ) {
        requireKey(key);
        Long result = template.execute(script, List.of(key), (Object[]) arguments);
        if (result == null) {
            throw new IllegalStateException("Redis script returned no result");
        }
        return result;
    }

    private void initializeNamespace() {
        template.opsForValue().setIfAbsent(namespaceKey, bytes(newNamespaceToken()));
    }

    private byte[] millis(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return bytes(Long.toString(Math.max(1L, ttl.toMillis())));
    }

    private Object requireValue(Object value) {
        return Objects.requireNonNull(value, "value");
    }

    private void requireCompatible(long scriptResult) {
        if (scriptResult < 0L) {
            throw new IllegalStateException("Redis cache value schema is incompatible");
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return key;
    }

    private String requirePrefix(String value) {
        if (value == null || value.isBlank() || value.indexOf('{') >= 0
            || value.indexOf('}') >= 0) {
            throw new IllegalArgumentException("keyPrefix is invalid");
        }
        return value;
    }

    private String requireName(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,126}")) {
            throw new IllegalArgumentException("cacheName is invalid");
        }
        return value;
    }

    private String requireSchemaId(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,126}")) {
            throw new IllegalArgumentException("schemaId is invalid");
        }
        return value;
    }

    private static String newNamespaceToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static DefaultRedisScript<Long> script(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
