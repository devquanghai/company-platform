package com.company.platform.logging.crypto.key;

import com.company.platform.logging.api.crypto.KeyProvider;
import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyReference;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class CachingKeyProvider implements KeyProvider, AutoCloseable {
    private final KeyProvider delegate;
    private final Duration ttl;
    private final int maximumSize;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public CachingKeyProvider(KeyProvider delegate, Duration ttl, int maximumSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
    }

    @Override public KeyMaterial resolveEncryptionKey(KeyReference reference) {
        return resolve("E", reference, () -> delegate.resolveEncryptionKey(reference));
    }

    @Override public KeyMaterial resolveDecryptionKey(KeyReference reference) {
        return resolve("D", reference, () -> delegate.resolveDecryptionKey(reference));
    }

    public void clear() {
        entries.values().forEach(entry -> entry.material.close());
        entries.clear();
    }

    @Override public void close() { clear(); }

    private KeyMaterial resolve(
        String operation, KeyReference reference, Supplier<KeyMaterial> loader
    ) {
        Instant now = Instant.now();
        String key = operation + "|" + reference.getAlias() + "|"
            + reference.getVersion() + "|" + reference.getAlgorithm();
        Entry current = entries.get(key);
        if (current != null && current.expiresAt.isAfter(now)
            && !current.material.isDestroyed()) {
            return current.material;
        }
        synchronized (entries) {
            current = entries.remove(key);
            if (current != null) {
                current.material.close();
            }
            evictIfNeeded();
            KeyMaterial loaded = loader.get();
            entries.put(key, new Entry(loaded, now.plus(ttl), now));
            return loaded;
        }
    }

    private void evictIfNeeded() {
        if (entries.size() < maximumSize) {
            return;
        }
        Map.Entry<String, Entry> oldest = entries.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().createdAt))
            .orElse(null);
        if (oldest != null && entries.remove(oldest.getKey(), oldest.getValue())) {
            oldest.getValue().material.close();
        }
    }

    private static final class Entry {
        private final KeyMaterial material;
        private final Instant expiresAt;
        private final Instant createdAt;
        private Entry(KeyMaterial material, Instant expiresAt, Instant createdAt) {
            this.material = material;
            this.expiresAt = expiresAt;
            this.createdAt = createdAt;
        }
    }
}
