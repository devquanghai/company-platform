package com.company.platform.core.utils;

import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class TextUtils {

    public boolean isEmpty(Object data) {
        if (data == null) {
            return true;
        }
        if (data instanceof String string) {
            return string.isEmpty();
        }
        if (data instanceof Iterable iterable) {
            return !iterable.iterator().hasNext();
        }
        if (data instanceof Object[] array) {
            return array.length == 0;
        }
        if (data instanceof Map map) {
            return map.isEmpty();
        }
        if (data instanceof CharSequence charSequence) {
            return charSequence.isEmpty();
        }
        return false;
    }

    public String toVietnameseSlug(String input) {
        if (input == null) {
            return null;
        }
        return input.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");
    }

    public String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input;
    }

    public String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }

        return prefix.endsWith(":")
            ? prefix
            : prefix + ":";
    }
}
