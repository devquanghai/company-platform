package com.company.platform.exchange.observability.internal.adapter.logging;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.lang.reflect.Array;
import java.util.regex.Pattern;

public final class DefaultOutboundDataMasker implements OutboundDataMasker {

    private static final String MASK = "***";
    private static final Set<String> SECRET_HEADERS = normalized(Set.of(
        "Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie",
        "X-Api-Key", "Api-Key", "X-Auth-Token", "Access-Token", "Refresh-Token"));
    private static final Set<String> SECRET_FIELDS = normalized(Set.of(
        "password", "secret", "token", "accessToken", "refreshToken", "apiKey",
        "clientSecret", "privateKey", "cardNumber", "cvv", "pin", "accountNumber"));
    private static final Pattern JSON_SECRET = Pattern.compile(
        "(?i)(\"(?:password|secret|token|accessToken|refreshToken|apiKey|clientSecret"
            + "|privateKey|cardNumber|cvv|pin|accountNumber)\"\\s*:\\s*\")([^\"]*)(\")");
    private static final Pattern JSON_NON_STRING_SECRET = Pattern.compile(
        "(?i)(\"(?:password|secret|token|accessToken|refreshToken|apiKey|clientSecret"
            + "|privateKey|cardNumber|cvv|pin|accountNumber)\"\\s*:\\s*)([^,}\\]]+)");
    private final JsonMapperHelper jsonMapperHelper;
    private final Set<String> headers;
    private final Set<String> fields;

    public DefaultOutboundDataMasker(JsonMapperHelper jsonMapperHelper, Set<String> additionalHeaders, Set<String> additionalFields
    ) {
        this.jsonMapperHelper = jsonMapperHelper;
        this.headers = union(SECRET_HEADERS, additionalHeaders);
        this.fields = union(SECRET_FIELDS, additionalFields);
    }

    @Override
    public HttpHeaders maskHeaders(HttpHeaders source) {
        HttpHeaders result = new HttpHeaders();
        source.forEach((name, values) ->
            result.put(name, headers.contains(normalize(name)) ? java.util.List.of(MASK) : values));
        return HttpHeaders.readOnlyHttpHeaders(result);
    }

    @Override
    public URI maskUri(URI uri) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
        var components = UriComponentsBuilder.fromUri(uri).build();
        components.getQueryParams().forEach((name, values) ->
            values.forEach(value -> builder.queryParam(name,
                fields.contains(normalize(name)) ? MASK : value)));
        return builder.build(true).toUri();
    }

    @Override
    public String maskBody(Object body, int maxLength) {
        if (body == null) {
            return "";
        }
        if (body instanceof byte[] || body instanceof ByteBuffer
            || body instanceof InputStream || body instanceof Resource) {
            return "<binary-or-stream-body-not-logged>";
        }
        String value = body instanceof CharSequence ? body.toString() : safeJson(body);
        String masked = JSON_SECRET.matcher(value).replaceAll("$1" + MASK + "$3");
        masked = JSON_NON_STRING_SECRET.matcher(masked).replaceAll("$1\"" + MASK + "\"");
        return truncate(masked, maxLength);
    }

    @Override
    public Map<String, Object> maskAttributes(Map<String, ?> attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        attributes.forEach((key, value) ->
            result.put(key, fields.contains(normalize(key)) ? MASK : maskValue(value)));
        return Map.copyOf(result);
    }

    private Object maskValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof byte[] || value instanceof ByteBuffer
            || value instanceof InputStream || value instanceof Resource) {
            return "<binary-or-stream-body-not-logged>";
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> nested = new LinkedHashMap<>();
            source.forEach((key, nestedValue) -> {
                String name = String.valueOf(key);
                nested.put(name, fields.contains(normalize(name))
                    ? MASK : maskValue(nestedValue));
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Iterable<?> values) {
            java.util.ArrayList<Object> nested = new java.util.ArrayList<>();
            values.forEach(item -> nested.add(maskValue(item)));
            return List.copyOf(nested);
        }
        if (value.getClass().isArray()) {
            java.util.ArrayList<Object> nested = new java.util.ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                nested.add(maskValue(Array.get(value, index)));
            }
            return List.copyOf(nested);
        }
        return value;
    }

    private String safeJson(Object body) {
        try {
            return jsonMapperHelper.toJson(body);
        } catch (RuntimeException exception) {
            return "<unserializable-body>";
        }
    }

    private static String truncate(String value, int maxLength) {
        if (maxLength < 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...<truncated>";
    }

    private static Set<String> union(Set<String> defaults, Set<String> additions) {
        java.util.HashSet<String> values = new java.util.HashSet<>(defaults);
        values.addAll(normalized(additions == null ? Set.of() : additions));
        return Set.copyOf(values);
    }

    private static Set<String> normalized(Set<String> values) {
        return values.stream().filter(StringUtils::hasText)
            .map(DefaultOutboundDataMasker::normalize)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("-", "");
    }
}
