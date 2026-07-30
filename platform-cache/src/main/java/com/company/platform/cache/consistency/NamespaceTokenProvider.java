package com.company.platform.cache.consistency;

public interface NamespaceTokenProvider {
    String current(String cacheName);

    String rotate(String cacheName);
}
