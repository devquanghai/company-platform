package com.company.platform.cache.internal.support;

import com.company.platform.cache.internal.application.port.out.CacheKeyEncoder;
import com.company.platform.cache.internal.application.resolver.NamedCacheDefinition;
import com.company.platform.core.json.JsonMapperHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DefaultCacheKeyEncoder implements CacheKeyEncoder {
    private static final int MAX_KEY_BYTES = 512;
    private static final Pattern SAFE_HASH_TAG = Pattern.compile("[a-zA-Z0-9._:-]{1,64}");

    private final JsonMapperHelper json;

    public DefaultCacheKeyEncoder(JsonMapperHelper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    @Override
    public String encode(
        NamedCacheDefinition cache,
        Object key,
        String namespaceToken
    ) {
        Objects.requireNonNull(cache, "cache must not be null");
        Objects.requireNonNull(key, "cache key must not be null");
        requireToken(namespaceToken);

        String version = requireSegment(cache.getProperties().getKey().getVersion(), "key version");
        String hashTag = cache.getProperties().getKey().getHashTag();
        String route = hashTag == null || hashTag.isBlank()
            ? cache.getName() : requireHashTag(hashTag);
        byte[] source = canonicalKeyBytes(key);
        boolean sensitive = cache.getProperties().getKey().isSensitive();
        String encoded = sensitive
            ? digest(source)
            : Base64.getUrlEncoder().withoutPadding().encodeToString(source);
        String physical = cache.getKeyPrefix()
            + ":{" + route + "}:" + cache.getName()
            + ":" + version + ":" + namespaceToken + ":" + encoded;
        if (physical.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES) {
            physical = cache.getKeyPrefix()
                + ":{" + route + "}:" + cache.getName()
                + ":" + version + ":" + namespaceToken + ":sha256:" + digest(source);
        }
        if (physical.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("Encoded cache key exceeds 512 UTF-8 bytes");
        }
        return physical;
    }

    private byte[] canonicalKeyBytes(Object key) {
        if (key instanceof String value) {
            return ("string:" + value).getBytes(StandardCharsets.UTF_8);
        }
        if (key instanceof Number value) {
            return ("number:" + value).getBytes(StandardCharsets.UTF_8);
        }
        if (key instanceof Boolean value) {
            return ("boolean:" + value).getBytes(StandardCharsets.UTF_8);
        }
        return json.toBytes(key);
    }

    private String digest(byte[] input) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String requireHashTag(String value) {
        if (!SAFE_HASH_TAG.matcher(value).matches()) {
            throw new IllegalArgumentException("Redis hash tag contains unsafe characters");
        }
        return value;
    }

    private String requireSegment(String value, String label) {
        if (value == null || !SAFE_HASH_TAG.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " contains unsafe characters");
        }
        return value;
    }

    private void requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new IllegalArgumentException("namespace token is invalid");
        }
    }
}
