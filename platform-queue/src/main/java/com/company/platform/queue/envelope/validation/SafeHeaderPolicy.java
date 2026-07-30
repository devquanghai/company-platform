package com.company.platform.queue.envelope.validation;

import com.company.platform.queue.envelope.header.PlatformMessageHeaders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SafeHeaderPolicy {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,62}");
    private final MessageLimits limits;
    private final Set<String> allowedCustomHeaders;

    public SafeHeaderPolicy(MessageLimits limits, Set<String> allowedCustomHeaders) {
        this.limits = limits;
        this.allowedCustomHeaders = Set.copyOf(
            allowedCustomHeaders == null ? Set.of() : allowedCustomHeaders);
    }

    public Map<String, String> sanitize(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (headers.size() > limits.maxHeaders()) {
            throw new IllegalArgumentException("header count exceeds limit");
        }
        Map<String, String> result = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = normalize(entry.getKey());
            String value = entry.getValue();
            if (!NAME.matcher(name).matches() || PlatformMessageHeaders.RESERVED.contains(name)
                || !allowedCustomHeaders.contains(name)) {
                throw new IllegalArgumentException("header is not allowed: " + safeName(name));
            }
            if (value == null || containsControl(value)) {
                throw new IllegalArgumentException("header value is invalid");
            }
            int bytes = name.getBytes(StandardCharsets.US_ASCII).length
                + value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > limits.maxHeaderBytes()) {
                throw new IllegalArgumentException("header exceeds size limit");
            }
            total += bytes;
            if (total > limits.maxTotalHeaderBytes()) {
                throw new IllegalArgumentException("total header size exceeds limit");
            }
            if (result.put(name, value) != null) {
                throw new IllegalArgumentException("duplicate canonical header");
            }
        }
        return Map.copyOf(result);
    }

    private String normalize(String name) {
        if (name == null || !StandardCharsets.US_ASCII.newEncoder().canEncode(name)) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private boolean containsControl(String value) {
        return value.chars().anyMatch(character ->
            character < 0x20 && character != '\t' || character == 0x7f);
    }

    private String safeName(String name) {
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
}
