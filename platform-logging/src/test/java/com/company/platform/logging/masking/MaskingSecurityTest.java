package com.company.platform.logging.masking;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.logging.annotation.masking.MaskCardNumber;
import com.company.platform.logging.annotation.masking.MaskEmail;
import com.company.platform.logging.annotation.masking.MaskPassword;
import com.company.platform.logging.annotation.masking.MaskPhone;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingOutcome;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.masking.registry.DefaultMaskingStrategyRegistry;
import com.company.platform.logging.masking.sanitizer.DefaultDataMaskingService;
import com.company.platform.logging.masking.strategy.FullMaskingStrategy;
import com.company.platform.logging.masking.strategy.HashMaskingStrategy;
import com.company.platform.logging.masking.strategy.PartialMaskingStrategy;
import com.company.platform.logging.masking.strategy.RemoveMaskingStrategy;
import com.company.platform.logging.masking.strategy.SubstitutionMaskingStrategy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingSecurityTest {

    @Test
    void strategiesCoverNullBoundsDomainHashHmacAndRemove() {
        var context = MaskingContext.builder().visiblePrefix(2).visibleSuffix(2)
            .substitution("[hidden]").build();

        assertThat(new FullMaskingStrategy().mask(null, context).getOutcome())
            .isEqualTo(MaskingOutcome.UNCHANGED);
        assertThat(new FullMaskingStrategy().mask("ab", context).getValue()).isEqualTo("***");
        assertThat(new FullMaskingStrategy().mask("abcdef", context).getValue())
            .isEqualTo("******");
        assertThat(new SubstitutionMaskingStrategy().mask("secret", context).getValue())
            .isEqualTo("[hidden]");
        assertThat(new SubstitutionMaskingStrategy().mask(null, context).getValue()).isNull();
        assertThat(new RemoveMaskingStrategy().mask("secret", context).getOutcome())
            .isEqualTo(MaskingOutcome.REMOVED);

        var partial = new PartialMaskingStrategy();
        assertThat(partial.mask("", context).getOutcome()).isEqualTo(MaskingOutcome.UNCHANGED);
        assertThat(partial.mask("12345678", context).getValue()).isEqualTo("12****78");
        assertThat(partial.mask("123", context).getValue()).isEqualTo("***");
        assertThat(partial.mask("alice@example.org",
            MaskingContext.builder().visiblePrefix(2).preserveDomain(true).build()).getValue())
            .isEqualTo("al***@example.org");

        var plainHash = new HashMaskingStrategy(null, null).mask("stable", context).getValue();
        assertThat(plainHash).startsWith("sha256:").hasSize(71);
        byte[] key = "01234567890123456789012345678901".getBytes();
        var hmac = new HashMaskingStrategy(alias -> key.clone(), "masking-key")
            .mask("stable", context).getValue();
        assertThat(hmac).startsWith("hmac-sha256:").isNotEqualTo(plainHash);
        assertThat(new HashMaskingStrategy(alias -> {
            throw new IllegalStateException("vault unavailable");
        }, "masking-key").mask("stable", context).getValue()).isEqualTo("***");
    }

    @Test
    void sanitizerMasksMandatoryConfiguredAnnotatedAndNestedValuesWithoutMutation() {
        PlatformLoggingProperties.MaskingProperties properties =
            new PlatformLoggingProperties.MaskingProperties();
        properties.getRules().add(rule("phone-rule", MaskingMatchType.FIELD_NAME,
            MaskingType.PARTIAL, List.of("mobile"), List.of(), List.of(), 3, 2));
        properties.getRules().add(rule("email-rule", MaskingMatchType.FIELD_NAME,
            MaskingType.PARTIAL, List.of("email"), List.of(), List.of(), 2, 0));
        properties.getRules().get(1).setPreserveDomain(true);
        properties.getRules().add(rule("remove-rule", MaskingMatchType.FIELD_NAME,
            MaskingType.REMOVE, List.of("discard"), List.of(), List.of(), 0, 0));
        properties.getRules().add(rule("path-rule", MaskingMatchType.JSON_PATH,
            MaskingType.SUBSTITUTION, List.of(), List.of("$.customer.taxId"), List.of(), 0, 0));
        properties.getRules().add(rule("regex-rule", MaskingMatchType.REGEX,
            MaskingType.SUBSTITUTION, List.of(), List.of(), List.of("SSN-\\d+"), 0, 0));
        var service = service(properties);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("password", "raw-password");
        source.put("mobile", "0901234567");
        source.put("discard", "remove-me");
        source.put("nullable", null);
        source.put("customer", new LinkedHashMap<>(Map.of("taxId", "TAX-42", "name", "Alice")));
        source.put("items", new ArrayList<>(java.util.Arrays.asList("safe", null)));

        Map<String, Object> sanitized = service.sanitizeFields(source);
        assertThat(sanitized).containsEntry("password", "***")
            .containsEntry("mobile", "090*****67")
            .containsEntry("nullable", null)
            .doesNotContainKey("discard");
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) sanitized.get("customer");
        assertThat(customer).containsEntry("taxId", "***");
        assertThat(source).containsEntry("password", "raw-password")
            .containsEntry("discard", "remove-me");
        assertThat(service.maskValue("API_KEY", "abc")).isEqualTo("***");
        assertThat(service.maskValue("normal", "value")).isEqualTo("value");
        assertThat(service.sanitizeMessage(
            "email=alice@example.org, mobile:'0901234567'"))
            .contains("email=al***@example.org", "mobile:'090*****67'")
            .doesNotContain("alice@example.org", "0901234567");

        var dto = new SensitiveDto();
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) service.sanitize(dto);
        assertThat(object).containsEntry("password", "***")
            .containsEntry("email", "al***@example.org")
            .containsEntry("phone", "090*****67")
            .containsEntry("card", "************1111");
        assertThat(dto.password).isEqualTo("do-not-change");

        assertThat(service.sanitize(ByteBuffer.wrap(new byte[] {1})))
            .isEqualTo("<binary-or-stream-not-logged>");
        assertThat(service.sanitize(new ByteArrayInputStream(new byte[] {1})))
            .isEqualTo("<binary-or-stream-not-logged>");
        assertThat(service.sanitize(new byte[] {1})).isEqualTo("<binary-or-stream-not-logged>");
        assertThat(service.sanitize(new int[] {1, 2})).isEqualTo(List.of(1, 2));
    }

    @Test
    void sanitizerHandlesJsonMessagesCyclesBoundsCollectionsAndThrowables() {
        PlatformLoggingProperties.MaskingProperties properties =
            new PlatformLoggingProperties.MaskingProperties();
        properties.setMaxStringLength(12);
        properties.setMaxCollectionSize(2);
        properties.setMaxMapSize(2);
        properties.setMaxDepth(2);
        var service = service(properties);

        assertThat(service.sanitizeJson("{\"password\":\"secret\",\"name\":\"A\"}"))
            .contains("\"password\":\"***\"").doesNotContain("secret");
        assertThat(service.sanitizeJson("{broken")).isEqualTo("<invalid-json>");
        assertThat(service.sanitizeJson(null)).isNull();
        assertThat(service.sanitizeMessage("password=secret\nnext")).doesNotContain("secret", "\n");
        assertThat(service.sanitizeMessage(null)).isNull();
        assertThat(service.sanitizeMessage("123456789012345678")).contains("...<truncated>");

        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        assertThat(service.sanitize(cyclic).toString()).contains("<cycle>");
        assertThat(service.sanitize(List.of("a", "b", "c")).toString()).contains("<truncated>");
        assertThat(service.sanitize(Map.of("a", Map.of("b", Map.of("c", "d")))).toString())
            .contains("<max-depth>");

        RuntimeException failure = new RuntimeException(
            "authorization=Bearer-secret", new IllegalArgumentException("password=child"));
        failure.addSuppressed(new IllegalStateException("api-key=hidden"));
        var sanitized = service.sanitizeThrowable(failure);
        assertThat(sanitized.getMessage()).doesNotContain("Bearer-secret");
        assertThat(sanitized.getCause().getMessage()).doesNotContain("child");
        assertThat(sanitized.getSuppressed()).hasSize(1);
        assertThat(service.sanitizeThrowable(null)).isNull();
    }

    private static DefaultDataMaskingService service(
        PlatformLoggingProperties.MaskingProperties properties
    ) {
        Map<String, com.company.platform.logging.api.masking.MaskingStrategy> strategies =
            new LinkedHashMap<>();
        strategies.put("full", new FullMaskingStrategy());
        strategies.put("partial", new PartialMaskingStrategy());
        strategies.put("substitution", new SubstitutionMaskingStrategy());
        strategies.put("remove", new RemoveMaskingStrategy());
        strategies.put("hash", new HashMaskingStrategy(null, null));
        return new DefaultDataMaskingService(
            new DefaultMaskingStrategyRegistry(strategies), properties, List.of(),
            new JsonMapperHelper(JsonMapper.builder().build()));
    }

    private static PlatformLoggingProperties.MaskingRuleProperties rule(
        String name, MaskingMatchType match, MaskingType masking, List<String> fields,
        List<String> paths, List<String> patterns, int prefix, int suffix
    ) {
        var rule = new PlatformLoggingProperties.MaskingRuleProperties();
        rule.setName(name);
        rule.setMatchType(match);
        rule.setMaskingType(masking);
        rule.setFields(new ArrayList<>(fields));
        rule.setPaths(new ArrayList<>(paths));
        rule.setPatterns(new ArrayList<>(patterns));
        rule.setVisiblePrefix(prefix);
        rule.setVisibleSuffix(suffix);
        return rule;
    }

    private static final class SensitiveDto {
        @MaskPassword String password = "do-not-change";
        @MaskEmail String email = "alice@example.org";
        @MaskPhone String phone = "0901234567";
        @MaskCardNumber String card = "4111111111111111";
    }
}
