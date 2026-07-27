package com.company.platform.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Objects;

@UtilityClass
public class CookieUtils {
    private static final boolean SECURE = true;
    private static final String SAME_SITE = "Strict";

    public static String getCookieValue(HttpServletRequest request, String name) {

        if (request == null || request.getCookies() == null || name == null) {
            return null;
        }

        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void addHttpOnlyCookie(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds
    ) {
        requireCookieArguments(response, name, maxAgeSeconds);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(name, value, maxAgeSeconds));
    }

    public void deleteCookie(HttpServletResponse response, String name) {
        requireCookieArguments(response, name, 0);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(name, "", 0));
    }

    private String buildCookie(
            String name,
            String value,
            int maxAgeSeconds
    ) {
        return ResponseCookie.from(name, value == null ? "" : value)
            .httpOnly(true)
            .secure(SECURE)
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(SAME_SITE)
            .build()
            .toString();
    }

    private void requireCookieArguments(
        HttpServletResponse response,
        String name,
        int maxAgeSeconds
    ) {
        Objects.requireNonNull(response, "response must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("cookie name must not be blank");
        }
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("maxAgeSeconds must not be negative");
        }
    }
}
