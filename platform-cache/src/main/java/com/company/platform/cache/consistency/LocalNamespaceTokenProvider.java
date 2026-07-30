package com.company.platform.cache.consistency;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LocalNamespaceTokenProvider implements NamespaceTokenProvider {
    private static final int TOKEN_BYTES = 16;

    private final ConcurrentMap<String, String> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom;

    public LocalNamespaceTokenProvider() {
        this(new SecureRandom());
    }

    LocalNamespaceTokenProvider(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public String current(String cacheName) {
        return tokens.computeIfAbsent(requireName(cacheName), ignored -> createToken());
    }

    @Override
    public String rotate(String cacheName) {
        String name = requireName(cacheName);
        String token = createToken();
        tokens.put(name, token);
        return token;
    }

    private String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String requireName(String cacheName) {
        if (cacheName == null || cacheName.isBlank()) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        return cacheName;
    }
}
