package com.company.platform.logging.logback.converter;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class BootstrapLogSanitizer {
    public static final String SAFE_FAILURE = "<sanitization-failed>";
    private static final Set<String> BOOTSTRAP_CREDENTIAL_KEYS = Set.of(
        "password", "passcode", "pin", "cvv", "authorization",
        "proxyauthorization", "cookie", "setcookie", "accesstoken",
        "refreshtoken", "apikey", "clientsecret", "privatekey");
    private static final Pattern BOOTSTRAP_CREDENTIAL_ASSIGNMENT = Pattern.compile(
        "(?i)(password|passcode|pin|cvv|authorization|proxy[-_]?authorization|"
            + "cookie|set[-_]?cookie|access[-_]?token|refresh[-_]?token|"
            + "api[-_]?key|client[-_]?secret|private[-_]?key)\\s*\"?\\s*[=:]\\s*"
            + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern CONTROLS = Pattern.compile("[\\p{Cntrl}]+");
    private static final int MAX = 16_384;

    private BootstrapLogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        try {
            String configured = LogbackMaskingLifecycle.sanitizeMessage(value);
            String bounded = configured.substring(
                0, Math.min(MAX, configured.length()));
            return CONTROLS.matcher(BOOTSTRAP_CREDENTIAL_ASSIGNMENT.matcher(bounded)
                .replaceAll("$1=***")).replaceAll(" ");
        } catch (RuntimeException exception) {
            return SAFE_FAILURE;
        }
    }

    public static String sanitize(String key, Object value) {
        if (BOOTSTRAP_CREDENTIAL_KEYS.contains(canonical(key))) {
            return "***";
        }
        String safe = value == null ? "" : safeValue(value);
        return sanitize(LogbackMaskingLifecycle.sanitizeValue(key, safe));
    }

    private static String safeValue(Object value) {
        return value instanceof CharSequence || value instanceof Number
            || value instanceof Boolean || value instanceof Enum<?>
            ? String.valueOf(value) : "<object-not-logged>";
    }

    private static String canonical(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }
}
