package com.company.platform.cache.adapter.redis;

import com.company.platform.cache.application.port.out.BackendUpdateResult;
import com.company.platform.core.json.JsonMapperHelper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RedisCacheBackendTest {

    @Test
    void supportsCrudAtomicVersionedAndLogicalClear() {
        FakeRedisTemplate template = new FakeRedisTemplate();
        RedisCacheBackend backend = backend(template);
        assertThat(backend.namespaceToken()).isNotBlank();
        assertThat(backend.get("key")).isEmpty();

        backend.put("key", "v1", Duration.ofMinutes(1));
        assertThat(template.entries).containsKey("key");
        assertThat(backend.get("key").orElseThrow().getValue()).isEqualTo("v1");
        assertThat(backend.get("key").orElseThrow().getVersion()).isEqualTo(1);
        assertThat(backend.putIfAbsent("key", "ignored", Duration.ofMinutes(1))).isFalse();
        assertThat(backend.putIfAbsent("other", "value", Duration.ofMinutes(1))).isTrue();
        assertThat(backend.compareAndSet("key", "v1", "v2")).isTrue();
        assertThat(backend.compareAndSet("key", "wrong", "v3")).isFalse();

        BackendUpdateResult conflict = backend.updateIfVersion("key", 1, "v3");
        assertThat(conflict.getStatus()).isEqualTo(BackendUpdateResult.Status.VERSION_CONFLICT);
        BackendUpdateResult updated = backend.updateIfVersion("key", 2, "v3");
        assertThat(updated.getStatus()).isEqualTo(BackendUpdateResult.Status.UPDATED);
        assertThat(backend.compute("key", value -> value + "!").getEntry().getValue())
            .isEqualTo("v3!");
        assertThat(backend.updateIfVersion("missing", 1, "x").getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);
        assertThat(backend.compute("missing", value -> value).getStatus())
            .isEqualTo(BackendUpdateResult.Status.NOT_FOUND);

        assertThat(backend.increment("counter", 2, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(backend.increment("counter", 3, Duration.ofMinutes(1))).isEqualTo(5);
        assertThat(backend.compareAndDelete("key", "wrong")).isFalse();
        assertThat(backend.compareAndDelete("key", "v3!")).isTrue();
        assertThat(backend.evict("other")).isTrue();
        assertThat(backend.estimatedSize()).isEqualTo(-1);

        String oldToken = backend.namespaceToken();
        var clear = backend.clear();
        assertThat(clear.getStrategy()).isEqualTo("REDIS_NAMESPACE_ROTATION");
        assertThat(clear.getPreviousNamespaceToken()).isEqualTo(oldToken);
        assertThat(clear.getCurrentNamespaceToken()).isNotEqualTo(oldToken);
        assertThat(clear.getExactDeletedCount()).isNull();
    }

    @Test
    void validatesInputsAndMissingScriptResult() {
        FakeRedisTemplate template = new FakeRedisTemplate();
        RedisCacheBackend backend = backend(template);
        assertThatNullPointerException().isThrownBy(() ->
            new RedisCacheBackend(null, json(), "app", "users"));
        assertThatNullPointerException().isThrownBy(() ->
            new RedisCacheBackend(template, null, "app", "users"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new RedisCacheBackend(template, json(), "{bad}", "users"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new RedisCacheBackend(template, json(), "app", "bad name"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new RedisCacheBackend(template, json(), "app", "users", "bad schema", 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new RedisCacheBackend(template, json(), "app", "users", "schema", 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
            backend.put(" ", "value", Duration.ofSeconds(1)));
        assertThatNullPointerException().isThrownBy(() ->
            backend.put("key", null, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
            backend.put("key", "value", Duration.ZERO));
        assertThatNullPointerException().isThrownBy(() ->
            backend.compute("key", null));

        template.nullScriptResult = true;
        assertThatIllegalStateException().isThrownBy(() ->
            backend.put("key", "value", Duration.ofSeconds(1)))
            .withMessageContaining("no result");
    }

    @Test
    void rejectsSchemaMismatchAndUnavailableNamespace() {
        FakeRedisTemplate template = new FakeRedisTemplate();
        RedisCacheBackend backend = backend(template);
        backend.put("key", "value", Duration.ofMinutes(1));
        template.entries.get("key").schemaId = bytes("other-schema");
        assertThatIllegalStateException().isThrownBy(() -> backend.get("key"))
            .withMessageContaining("schema");
        assertThatIllegalStateException().isThrownBy(() ->
            backend.compareAndSet("key", "value", "new"))
            .withMessageContaining("schema");
        assertThatIllegalStateException().isThrownBy(() ->
            backend.compareAndDelete("key", "value"))
            .withMessageContaining("schema");
        assertThatIllegalStateException().isThrownBy(() ->
            backend.updateIfVersion("key", 1, "new"))
            .withMessageContaining("schema");

        template.values.clear();
        template.suppressNamespaceWrites = true;
        assertThatIllegalStateException().isThrownBy(backend::namespaceToken)
            .withMessageContaining("unavailable");
    }

    private RedisCacheBackend backend(FakeRedisTemplate template) {
        return new RedisCacheBackend(template, json(), "application:cache", "users");
    }

    private JsonMapperHelper json() {
        return new JsonMapperHelper(JsonMapper.builder().build());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeRedisTemplate extends RedisTemplate<String, byte[]> {
        private final Map<String, Entry> entries = new HashMap<>();
        private final Map<String, byte[]> values = new HashMap<>();
        private boolean nullScriptResult;
        private boolean suppressNamespaceWrites;

        @SuppressWarnings("unchecked")
        @Override
        public <HK, HV> HashOperations<String, HK, HV> opsForHash() {
            return (HashOperations<String, HK, HV>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HashOperations.class},
                (proxy, method, args) -> {
                    if ("multiGet".equals(method.getName())) {
                        Entry entry = entries.get((String) args[0]);
                        if (entry == null) {
                            return java.util.Arrays.asList(null, null, null, null);
                        }
                        return List.of(entry.payload, bytes(Long.toString(entry.version)),
                            entry.schemaId, entry.schemaVersion);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        }

        @SuppressWarnings("unchecked")
        @Override
        public ValueOperations<String, byte[]> opsForValue() {
            return (ValueOperations<String, byte[]>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setIfAbsent" -> setNamespace(
                        (String) args[0], (byte[]) args[1]);
                    case "get" -> values.get((String) args[0]);
                    case "getAndSet" -> values.put((String) args[0], (byte[]) args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }

        private boolean setNamespace(String key, byte[] value) {
            return !suppressNamespaceWrites && values.putIfAbsent(key, value) == null;
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            return entries.containsKey(key) ? 60_000L : -2L;
        }

        @Override
        public Boolean delete(String key) {
            return entries.remove(key) != null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T execute(
            RedisScript<T> script, List<String> keys, Object... args
        ) {
            if (nullScriptResult) {
                return null;
            }
            String source = script.getScriptAsString();
            String key = keys.getFirst();
            Entry entry = entries.get(key);
            long result;
            if (source.contains("EXISTS")) {
                if (entry != null) {
                    result = 0;
                } else {
                    entries.put(key, new Entry(
                        (byte[]) args[0], 1, (byte[]) args[2], (byte[]) args[3]));
                    result = 1;
                }
            } else if (source.contains("current ~= ARGV[1]")
                && source.contains("HINCRBY")) {
                if (!compatible(entry, (byte[]) args[2], (byte[]) args[3])) {
                    return (T) Long.valueOf(-1);
                }
                if (entry != null && java.util.Arrays.equals(entry.payload, (byte[]) args[0])) {
                    entry.payload = (byte[]) args[1];
                    entry.version++;
                    result = 1;
                } else {
                    result = 0;
                }
            } else if (source.contains("current ~= ARGV[1]")) {
                if (!compatible(entry, (byte[]) args[1], (byte[]) args[2])) {
                    return (T) Long.valueOf(-1);
                }
                if (entry != null && java.util.Arrays.equals(entry.payload, (byte[]) args[0])) {
                    entries.remove(key);
                    result = 1;
                } else {
                    result = 0;
                }
            } else if (source.contains("if version ~= ARGV[1]")) {
                if (entry == null) {
                    result = -1;
                } else if (!compatible(entry, (byte[]) args[3], (byte[]) args[4])) {
                    result = -2;
                } else if (entry.version != number((byte[]) args[0])) {
                    result = 0;
                } else {
                    entry.payload = (byte[]) args[1];
                    entry.version = number((byte[]) args[2]);
                    result = 1;
                }
            } else if (source.contains("return redis.call('HGET'")) {
                long delta = number((byte[]) args[0]);
                if (entry == null) {
                    entries.put(key, new Entry(
                        bytes(Long.toString(delta)), 1,
                        (byte[]) args[2], (byte[]) args[3]));
                    result = delta;
                } else {
                    result = number(entry.payload) + delta;
                    entry.payload = bytes(Long.toString(result));
                    entry.version++;
                }
            } else {
                long version = entry == null ? 1 : entry.version + 1;
                entries.put(key, new Entry(
                    (byte[]) args[0], version, (byte[]) args[2], (byte[]) args[3]));
                result = 1;
            }
            return (T) Long.valueOf(result);
        }

        private static long number(byte[] value) {
            return Long.parseLong(new String(value, StandardCharsets.UTF_8));
        }

        private static byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        private static boolean compatible(
            Entry entry, byte[] schemaId, byte[] schemaVersion
        ) {
            return entry != null
                && java.util.Arrays.equals(entry.schemaId, schemaId)
                && java.util.Arrays.equals(entry.schemaVersion, schemaVersion);
        }
    }

    private static final class Entry {
        private byte[] payload;
        private long version;
        private byte[] schemaId;
        private byte[] schemaVersion;
        private Entry(
            byte[] payload, long version, byte[] schemaId, byte[] schemaVersion
        ) {
            this.payload = payload;
            this.version = version;
            this.schemaId = schemaId;
            this.schemaVersion = schemaVersion;
        }
    }
}
