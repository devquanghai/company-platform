package com.company.platform.core.utils;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@UtilityClass
public class Base64Utils {

    // =========================================================
    // ENCODE
    // =========================================================

    /**
     * Encode raw bytes -> Base64 string
     */
    public String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(
            Objects.requireNonNull(data, "data must not be null")
        );
    }

    /**
     * Encode string (UTF-8) -> Base64 string
     */
    public String encode(String data) {
        return encode(Objects.requireNonNull(data, "data must not be null")
            .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encode URL-safe (JWT, token, query param)
     */
    public String encodeUrlSafe(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            Objects.requireNonNull(data, "data must not be null")
        );
    }

    public String encodeUrlSafe(String data) {
        return encodeUrlSafe(Objects.requireNonNull(data, "data must not be null")
            .getBytes(StandardCharsets.UTF_8));
    }

    // =========================================================
    // DECODE
    // =========================================================

    /**
     * Decode Base64 -> raw bytes
     */
    public byte[] decode(String base64) {
        return Base64.getDecoder().decode(
            Objects.requireNonNull(base64, "base64 must not be null")
        );
    }

    /**
     * Decode Base64 -> String (UTF-8)
     */
    public String decodeToString(String base64) {
        return new String(decode(base64), StandardCharsets.UTF_8);
    }

    /**
     * Decode URL-safe Base64
     */
    public byte[] decodeUrlSafe(String base64) {
        return Base64.getUrlDecoder().decode(
            Objects.requireNonNull(base64, "base64 must not be null")
        );
    }

    public String decodeUrlSafeToString(String base64) {
        return new String(decodeUrlSafe(base64), StandardCharsets.UTF_8);
    }
}
