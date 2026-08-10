package com.company.platform.cache.internal.consistency;

public interface NamespaceTokenProvider {
    String current(String cacheName);

    String rotate(String cacheName);
}
