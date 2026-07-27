package com.company.platform.logging.domain.model;

import java.security.Key;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KeyMaterial implements AutoCloseable {
    private final String alias;
    private final KeyVersion version;
    private final KeyPurpose purpose;
    private final CryptoAlgorithm algorithm;
    private final Key key;
    private final AtomicBoolean destroyed = new AtomicBoolean();

    public KeyMaterial(
        String alias, KeyVersion version, KeyPurpose purpose,
        CryptoAlgorithm algorithm, Key key
    ) {
        this.alias = Objects.requireNonNull(alias, "alias");
        this.version = Objects.requireNonNull(version, "version");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.key = Objects.requireNonNull(key, "key");
    }

    public String alias() { return alias; }
    public KeyVersion version() { return version; }
    public KeyPurpose purpose() { return purpose; }
    public CryptoAlgorithm algorithm() { return algorithm; }
    public Key key() {
        if (destroyed.get()) {
            throw new IllegalStateException("Key material has been destroyed");
        }
        return key;
    }
    public boolean isDestroyed() { return destroyed.get(); }

    @Override
    public void close() {
        if (destroyed.compareAndSet(false, true)
            && key instanceof javax.security.auth.Destroyable destroyable) {
            try {
                destroyable.destroy();
            } catch (javax.security.auth.DestroyFailedException ignored) {
                // Best effort; no key data is logged.
            }
        }
    }

    @Override public String toString() {
        return "KeyMaterial(alias=<redacted>, version=<redacted>, purpose="
            + purpose + ", algorithm=" + algorithm + ", destroyed=" + destroyed + ")";
    }
}
