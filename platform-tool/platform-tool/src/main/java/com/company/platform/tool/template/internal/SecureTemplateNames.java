package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.TemplateRenderException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SecureTemplateNames {
    private static final Pattern SAFE = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}");
    private SecureTemplateNames() { }
    public static String normalize(String value) {
        if (value == null) throw new TemplateRenderException("Template id is required");
        String decoded = value;
        for (int i = 0; i < 3; i++) decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
        decoded = decoded.trim();
        String lower = decoded.toLowerCase(Locale.ROOT);
        if (!SAFE.matcher(decoded).matches() || lower.contains("..") || lower.contains("%")) {
            throw new TemplateRenderException("Invalid template id");
        }
        return decoded;
    }
}
