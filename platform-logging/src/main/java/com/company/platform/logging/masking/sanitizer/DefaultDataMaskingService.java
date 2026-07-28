package com.company.platform.logging.masking.sanitizer;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.api.masking.DataMaskingService;
import com.company.platform.logging.api.masking.MaskingRuleProvider;
import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.api.masking.MaskingStrategyRegistry;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingOutcome;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingRule;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import com.company.platform.logging.domain.model.SanitizedThrowable;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class DefaultDataMaskingService implements DataMaskingService {

    private static final String BINARY = "<binary-or-stream-not-logged>";
    private static final String CYCLE = "<cycle>";
    private static final String INACCESSIBLE = "<inaccessible>";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern NEW_LINES = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern MANDATORY_MESSAGE = Pattern.compile(
        "(?i)(password|passcode|pin|cvv|authorization|proxy[-_]?authorization|"
            + "cookie|set[-_]?cookie|access[-_]?token|refresh[-_]?token|"
            + "api[-_]?key|client[-_]?secret|private[-_]?key)\\s*\"?\\s*[=:]\\s*"
            + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Set<String> BASELINE_MANDATORY = Set.of(
        "password", "passcode", "pin", "cvv", "authorization",
        "proxyauthorization", "cookie", "setcookie", "accesstoken",
        "refreshtoken", "apikey", "clientsecret", "privatekey");

    private final MaskingStrategyRegistry strategies;
    private final PlatformLoggingProperties.MaskingProperties properties;
    private final Set<String> mandatory;
    private final List<CompiledRule> rules;
    private final JsonMapperHelper jsonMapperHelper;

    public DefaultDataMaskingService(MaskingStrategyRegistry strategies,
        PlatformLoggingProperties.MaskingProperties properties,
        List<MaskingRuleProvider> providers,
        JsonMapperHelper jsonMapperHelper
    ) {
        this.strategies = strategies;
        this.properties = properties;
        this.jsonMapperHelper = jsonMapperHelper;
        this.mandatory = java.util.stream.Stream.concat(
                BASELINE_MANDATORY.stream(),
                properties.getMandatoryFields().stream()
                    .map(DefaultDataMaskingService::canonical))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ArrayList<MaskingRule> configured = new ArrayList<>();
        properties.getRules().stream()
            .map(DefaultDataMaskingService::toRule).forEach(configured::add);
        providers.forEach(provider -> configured.addAll(provider.rules()));
        this.rules = configured.stream().map(CompiledRule::new).toList();
    }

    @Override
    public String maskValue(String fieldName, String value) {
        MaskingRule rule = ruleFor(fieldName, "$." + fieldName, null);
        return apply(value, rule).getValue();
    }

    @Override
    public Object sanitize(Object source) {
        return sanitize(source, "$", 0, new IdentityHashMap<>(), null);
    }

    @Override
    public Object sanitizeAnnotated(Object source, Sensitive annotation) {
        if (source == null || annotation == null) {
            return sanitize(source);
        }
        return apply(scalar(source), annotation).getValue();
    }

    @Override
    public String sanitizeJson(String source) {
        if (source == null) {
            return null;
        }
        try {
            Object tree = jsonMapperHelper.fromJson(source, Object.class);
            return jsonMapperHelper.toJson(sanitize(tree));
        } catch (RuntimeException exception) {
            log.warn("Failed to parse JSON for sanitization: {}", exception.getMessage());
            return "<invalid-json>";
        }
    }

    @Override
    public String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String bounded = truncate(message);
        String masked = MANDATORY_MESSAGE.matcher(bounded)
            .replaceAll("$1=***");
        for (CompiledRule rule : rules) {
            if (rule.rule.getMatchType() == MaskingMatchType.FIELD_NAME) {
                masked = replaceConfiguredFields(masked, rule);
            } else if (rule.rule.getMatchType() == MaskingMatchType.REGEX) {
                masked = rule.replace(masked);
            }
        }
        return sanitizeControls(masked);
    }

    private String replaceConfiguredFields(String message, CompiledRule compiled) {
        String result = message;
        for (Pattern pattern : compiled.messagePatterns) {
            Matcher matcher = pattern.matcher(result);
            StringBuilder output = new StringBuilder();
            while (matcher.find()) {
                String doubleQuoted = matcher.group(2);
                String singleQuoted = matcher.group(3);
                String raw = doubleQuoted != null
                    ? doubleQuoted
                    : singleQuoted != null ? singleQuoted : matcher.group(4);
                MaskingResult maskingResult = apply(raw, compiled.rule);
                String safe = maskingResult.getOutcome() == MaskingOutcome.REMOVED
                    ? "<removed>"
                    : maskingResult.getValue();
                String quote = doubleQuoted != null ? "\""
                    : singleQuoted != null ? "'" : "";
                matcher.appendReplacement(output, Matcher.quoteReplacement(
                    matcher.group(1) + quote + safe + quote));
            }
            matcher.appendTail(output);
            result = output.toString();
        }
        return result;
    }

    @Override
    public Map<String, Object> sanitizeFields(Map<String, ?> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Object value = sanitize(fields);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> safe = (Map<String, Object>) map;
            return safe;
        }
        return Map.of();
    }

    @Override
    public SanitizedThrowable sanitizeThrowable(Throwable throwable) {
        return throwable == null ? null : throwable(throwable, 0, new IdentityHashMap<>());
    }

    private Object sanitize(
        Object source, String path, int depth,
        IdentityHashMap<Object, Boolean> visited, Sensitive annotation
    ) {
        if (source == null) {
            return null;
        }
        if (depth >= properties.getMaxDepth()) {
            return "<max-depth>";
        }
        if (source instanceof CharSequence || source instanceof Character) {
            String value = sanitizeMessage(source.toString());
            return annotation == null ? value : apply(value, annotation).getValue();
        }
        if (source instanceof Number || source instanceof Boolean) {
            return annotation == null ? source : apply(String.valueOf(source), annotation).getValue();
        }
        if (source instanceof Enum<?> value) {
            return value.name();
        }
        if (source instanceof TemporalAccessor || source instanceof UUID
            || source instanceof URI) {
            return sanitizeMessage(String.valueOf(source));
        }
        if (isDenied(source)) {
            return BINARY;
        }
        if (visited.put(source, Boolean.TRUE) != null) {
            return CYCLE;
        }
        try {
            if (source instanceof Map<?, ?> map) {
                return sanitizeMap(map, path, depth, visited);
            }
            if (source instanceof Iterable<?> iterable) {
                return sanitizeIterable(iterable, path, depth, visited);
            }
            if (source.getClass().isArray()) {
                return sanitizeArray(source, path, depth, visited);
            }
            return sanitizeObject(source, path, depth, visited);
        } finally {
            visited.remove(source);
        }
    }

    private Map<String, Object> sanitizeMap(
        Map<?, ?> source, String path, int depth, IdentityHashMap<Object, Boolean> visited
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (count++ >= properties.getMaxMapSize()) {
                result.put("_truncated", true);
                break;
            }
            String key = safeKey(entry.getKey());
            MaskingRule rule = ruleFor(key, path + "." + key, null);
            if (rule != null) {
                MaskingResult masked = apply(scalar(entry.getValue()), rule);
                if (masked.getOutcome() != MaskingOutcome.REMOVED) {
                    result.put(key, masked.getValue());
                }
            } else {
                result.put(key, sanitize(entry.getValue(), path + "." + key,
                    depth + 1, visited, null));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Object> sanitizeIterable(
        Iterable<?> source, String path, int depth, IdentityHashMap<Object, Boolean> visited
    ) {
        ArrayList<Object> result = new ArrayList<>();
        int index = 0;
        for (Object value : source) {
            if (index >= properties.getMaxCollectionSize()) {
                result.add("<truncated>");
                break;
            }
            result.add(sanitize(value, path + "[" + index + "]", depth + 1, visited, null));
            index++;
        }
        return Collections.unmodifiableList(result);
    }

    private List<Object> sanitizeArray(
        Object source, String path, int depth, IdentityHashMap<Object, Boolean> visited
    ) {
        int size = Math.min(Array.getLength(source), properties.getMaxCollectionSize());
        ArrayList<Object> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(sanitize(Array.get(source, index), path + "[" + index + "]",
                depth + 1, visited, null));
        }
        if (Array.getLength(source) > size) {
            result.add("<truncated>");
        }
        return Collections.unmodifiableList(result);
    }

    private Map<String, Object> sanitizeObject(
        Object source, String path, int depth, IdentityHashMap<Object, Boolean> visited
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Class<?> type = source.getClass();
        int count = 0;
        for (Field field : fields(type)) {
            if (count++ >= properties.getMaxMapSize()) {
                result.put("_truncated", true);
                break;
            }
            String name = field.getName();
            Sensitive sensitive = sensitive(field);
            MaskingRule rule = ruleFor(name, path + "." + name, sensitive);
            if (!field.trySetAccessible()) {
                result.put(name, INACCESSIBLE);
                continue;
            }
            try {
                Object value = field.get(source);
                if (rule != null) {
                    MaskingResult masked = apply(scalar(value), rule);
                    if (masked.getOutcome() != MaskingOutcome.REMOVED) {
                        result.put(name, masked.getValue());
                    }
                } else {
                    result.put(name, sanitize(value, path + "." + name,
                        depth + 1, visited, sensitive));
                }
            } catch (IllegalAccessException exception) {
                result.put(name, INACCESSIBLE);
            }
        }
        if (result.isEmpty()) {
            return Map.of("_type", type.getName());
        }
        return Collections.unmodifiableMap(result);
    }

    private MaskingRule ruleFor(String field, String path, Sensitive sensitive) {
        if (mandatory.contains(canonical(field))) {
            return mandatoryRule(field);
        }
        if (sensitive != null) {
            return annotationRule(field, sensitive);
        }
        for (CompiledRule compiled : rules) {
            if (compiled.matchesPath(path)) {
                return compiled.rule;
            }
        }
        for (CompiledRule compiled : rules) {
            if (compiled.matchesField(field)) {
                return compiled.rule;
            }
        }
        return null;
    }

    private MaskingResult apply(String value, Sensitive sensitive) {
        return apply(value, annotationRule("annotation", sensitive));
    }

    private MaskingResult apply(String value, MaskingRule rule) {
        if (rule == null) {
            return MaskingResult.unchanged(sanitizeMessage(value));
        }
        MaskingStrategy strategy = rule.getStrategyBean() != null
            && !rule.getStrategyBean().isBlank()
            ? strategies.find(rule.getStrategyBean()).orElseGet(
                () -> required(rule.getMaskingType()))
            : required(rule.getMaskingType());
        return strategy.mask(value, MaskingContext.builder()
            .fieldName(rule.getName()).piiType(rule.getPiiType())
            .visiblePrefix(rule.getVisiblePrefix())
            .visibleSuffix(rule.getVisibleSuffix())
            .substitution(rule.getSubstitution())
            .preserveDomain(rule.isPreserveDomain())
            .strategyBean(rule.getStrategyBean()).build());
    }

    private MaskingStrategy required(MaskingType type) {
        return strategies.find(type).orElseThrow(
            () -> new IllegalStateException("Masking strategy is unavailable: " + type));
    }

    private SanitizedThrowable throwable(
        Throwable source, int depth, IdentityHashMap<Object, Boolean> visited
    ) {
        if (depth >= 5 || visited.put(source, Boolean.TRUE) != null) {
            return SanitizedThrowable.builder().type("<truncated>").build();
        }
        try {
            List<String> stack = java.util.Arrays.stream(source.getStackTrace())
                .limit(64)
                .map(frame -> truncate(frame.getClassName() + "." + frame.getMethodName()
                    + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")"))
                .toList();
            List<SanitizedThrowable> suppressed = java.util.Arrays.stream(source.getSuppressed())
                .limit(8).map(value -> throwable(value, depth + 1, visited)).toList();
            return SanitizedThrowable.builder()
                .type(source.getClass().getName())
                .message(sanitizeMessage(source.getMessage()))
                .stackTrace(stack).suppressed(suppressed)
                .cause(source.getCause() == null ? null
                    : throwable(source.getCause(), depth + 1, visited))
                .build();
        } finally {
            visited.remove(source);
        }
    }

    private String truncate(String value) {
        if (value.length() <= properties.getMaxStringLength()) {
            return value;
        }
        return value.substring(0, properties.getMaxStringLength()) + "...<truncated>";
    }

    private String sanitizeControls(String value) {
        if (!properties.isSanitizeControlCharacters()) {
            return value;
        }
        return CONTROL.matcher(NEW_LINES.matcher(value).replaceAll(" ")).replaceAll("?");
    }

    private static List<Field> fields(Class<?> type) {
        ArrayList<Field> result = new ArrayList<>();
        for (Class<?> current = type;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    private static boolean isDenied(Object value) {
        String name = value.getClass().getName();
        return value instanceof byte[] || value instanceof ByteBuffer
            || value instanceof InputStream || value instanceof Reader
            || value instanceof Path || value instanceof File
            || name.startsWith("jakarta.servlet.")
            || name.startsWith("org.springframework.web.multipart.")
            || name.startsWith("org.springframework.core.io.")
            || name.startsWith("org.springframework.core.io.buffer.")
            || name.startsWith("org.reactivestreams.")
            || name.startsWith("reactor.core.publisher.");
    }

    private static String safeKey(Object key) {
        if (key instanceof CharSequence || key instanceof Number || key instanceof Enum<?>) {
            return String.valueOf(key);
        }
        return "<key>";
    }

    private static String scalar(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number
            || value instanceof Boolean || value instanceof Character
            || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return "<object-not-logged>";
    }

    private static Sensitive sensitive(Field field) {
        Sensitive direct = field.getAnnotation(Sensitive.class);
        if (direct != null) {
            return direct;
        }
        for (Annotation annotation : field.getAnnotations()) {
            Sensitive meta = annotation.annotationType().getAnnotation(Sensitive.class);
            if (meta != null) {
                return meta;
            }
        }
        return null;
    }

    private static String canonical(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private static MaskingRule mandatoryRule(String field) {
        return MaskingRule.builder().name(field).mandatory(true)
            .piiType(PiiType.GENERIC).maskingType(MaskingType.SUBSTITUTION)
            .substitution("***").build();
    }

    private static MaskingRule annotationRule(String field, Sensitive value) {
        return MaskingRule.builder().name(field).matchType(MaskingMatchType.ANNOTATION)
            .piiType(value.piiType()).maskingType(value.masking())
            .visiblePrefix(value.visiblePrefix()).visibleSuffix(value.visibleSuffix())
            .substitution(value.substitution()).preserveDomain(value.preserveDomain())
            .strategyBean(value.strategyBean()).build();
    }

    private static MaskingRule toRule(
        PlatformLoggingProperties.MaskingRuleProperties value
    ) {
        List<String> expressions = switch (value.getMatchType()) {
            case JSON_PATH -> value.getPaths();
            case REGEX -> value.getPatterns();
            default -> value.getFields();
        };
        return MaskingRule.builder().name(value.getName())
            .matchType(value.getMatchType()).expressions(expressions)
            .piiType(value.getPiiType()).maskingType(value.getMaskingType())
            .visiblePrefix(value.getVisiblePrefix())
            .visibleSuffix(value.getVisibleSuffix())
            .substitution(value.getSubstitution())
            .preserveDomain(value.isPreserveDomain())
            .strategyBean(value.getStrategyBean()).build();
    }

    private static final class CompiledRule {
        private final MaskingRule rule;
        private final Set<String> fields;
        private final List<Pattern> patterns;
        private final List<Pattern> messagePatterns;

        private CompiledRule(MaskingRule rule) {
            this.rule = rule;
            this.fields = rule.getExpressions().stream()
                .map(DefaultDataMaskingService::canonical)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            this.patterns = rule.getMatchType() == MaskingMatchType.JSON_PATH
                ? rule.getExpressions().stream()
                    .map(expression -> Pattern.compile(jsonPathRegex(expression)))
                    .toList()
                : rule.getMatchType() == MaskingMatchType.REGEX
                    ? rule.getExpressions().stream().map(Pattern::compile).toList()
                    : List.of();
            this.messagePatterns = rule.getMatchType() == MaskingMatchType.FIELD_NAME
                ? rule.getExpressions().stream()
                    .map(CompiledRule::messageFieldPattern)
                    .toList()
                : List.of();
        }

        private boolean matchesField(String field) {
            return rule.getMatchType() != MaskingMatchType.JSON_PATH
                && rule.getMatchType() != MaskingMatchType.REGEX
                && fields.contains(canonical(field));
        }

        private boolean matchesPath(String path) {
            return rule.getMatchType() == MaskingMatchType.JSON_PATH
                && patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
        }

        private String replace(String value) {
            String result = value;
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(result);
                result = matcher.replaceAll(
                    Matcher.quoteReplacement(rule.getSubstitution()));
            }
            return result;
        }

        private static Pattern messageFieldPattern(String field) {
            return Pattern.compile(
                "(?i)([\"']?" + Pattern.quote(field)
                    + "[\"']?\\s*[=:]\\s*)(?:\"([^\"]*)\"|'([^']*)'|"
                    + "([^\\s,;}\\]]+))"
            );
        }

        private static String jsonPathRegex(String expression) {
            if (expression == null || !expression.startsWith("$")) {
                throw new IllegalArgumentException("JSON path must start with $");
            }
            StringBuilder result = new StringBuilder("^");
            for (int index = 0; index < expression.length();) {
                if (expression.startsWith("[*]", index)) {
                    result.append("\\[\\d+\\]");
                    index += 3;
                } else {
                    char current = expression.charAt(index++);
                    if ("\\.^$|?*+()[]{}".indexOf(current) >= 0) {
                        result.append('\\');
                    }
                    result.append(current);
                }
            }
            return result.append('$').toString();
        }
    }
}
