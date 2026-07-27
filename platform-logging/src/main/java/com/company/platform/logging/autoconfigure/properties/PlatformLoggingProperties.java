package com.company.platform.logging.autoconfigure.properties;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import com.company.platform.logging.domain.model.StructuredLogFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "platform.logging")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformLoggingProperties {

    boolean enabled = true;
    String environment = "unknown";
    @Valid ConsoleProperties console = new ConsoleProperties();
    @Valid FileProperties file = new FileProperties();
    @Valid StructuredProperties structured = new StructuredProperties();
    @Valid AsyncProperties async = new AsyncProperties();
    @Valid ContextProperties context = new ContextProperties();
    @Valid MethodLoggingProperties methodLogging = new MethodLoggingProperties();
    @Valid MaskingProperties masking = new MaskingProperties();
    @Valid CryptoProperties crypto = new CryptoProperties();
    @Valid AuditProperties audit = new AuditProperties();
    @Valid SecurityProperties security = new SecurityProperties();
    @Valid MetricsProperties metrics = new MetricsProperties();

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ConsoleProperties {
        boolean enabled = true;
        StructuredLogFormat format = StructuredLogFormat.TEXT;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class FileProperties {
        boolean enabled;
        String path = "./logs";
        String name = "application.log";
        @Valid RollingPolicyProperties rollingPolicy = new RollingPolicyProperties();
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RollingPolicyProperties {
        DataSize maxFileSize = DataSize.ofMegabytes(100);
        @Min(1) int maxHistory = 30;
        DataSize totalSizeCap = DataSize.ofGigabytes(10);
        boolean cleanHistoryOnStart;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StructuredProperties {
        boolean enabled;
        StructuredLogFormat format = StructuredLogFormat.ECS;
        boolean includeServiceMetadata = true;
        boolean includeHostMetadata = true;
        boolean includeProcessMetadata = true;
        boolean includeMdc = true;
        @Min(256) int maxStackTraceLength = 16_384;
        String customFormatter;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AsyncProperties {
        boolean enabled = true;
        @Min(1) int queueSize = 8192;
        @Min(0) int discardingThreshold;
        boolean neverBlock;
        Duration flushTimeout = Duration.ofSeconds(5);
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ContextProperties {
        UserIdMode userIdMode = UserIdMode.OMIT;
    }

    public enum UserIdMode {
        OMIT, MASK, HASH
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MethodLoggingProperties {
        boolean enabled = true;
        boolean includeArgumentsByDefault;
        boolean includeResultByDefault;
        boolean includeDuration = true;
        boolean includeException = true;
        @Min(1) int maxArgumentLength = 4096;
        @Min(1) int maxResultLength = 4096;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MaskingProperties {
        boolean enabled = true;
        boolean failOnInvalidRule = true;
        MaskingType defaultType = MaskingType.SUBSTITUTION;
        String defaultSubstitution = "***";
        boolean sanitizeExceptionMessage = true;
        boolean sanitizeMdc = true;
        boolean sanitizeKeyValues = true;
        boolean sanitizeControlCharacters = true;
        @Min(1) int maxDepth = 10;
        @Min(1) int maxStringLength = 4096;
        @Min(1) int maxCollectionSize = 100;
        @Min(1) int maxMapSize = 100;
        List<String> mandatoryFields = new ArrayList<>(List.of(
            "password", "passcode", "pin", "cvv", "authorization",
            "proxy-authorization", "cookie", "set-cookie", "access-token",
            "refresh-token", "api-key", "client-secret", "private-key"));
        @Valid List<MaskingRuleProperties> rules = new ArrayList<>();
        String hmacKeyAlias;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MaskingRuleProperties {
        String name;
        boolean enabled = true;
        MaskingMatchType matchType = MaskingMatchType.FIELD_NAME;
        List<String> fields = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> patterns = new ArrayList<>();
        PiiType piiType = PiiType.GENERIC;
        MaskingType maskingType = MaskingType.SUBSTITUTION;
        int visiblePrefix;
        int visibleSuffix;
        String substitution = "***";
        boolean preserveDomain;
        String strategyBean;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CryptoProperties {
        boolean enabled = true;
        boolean annotationEnabled = true;
        boolean failIfDisabledAnnotationUsed = true;
        boolean allowLegacyAlgorithms;
        @Valid CryptoDefaultsProperties defaults = new CryptoDefaultsProperties();
        @Valid CryptoProvidersProperties providers = new CryptoProvidersProperties();
        @Valid KeyCacheProperties keyCache = new KeyCacheProperties();
        @Min(1024) int maxEnvelopeLength = 1_048_576;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CryptoDefaultsProperties {
        CryptoProviderType provider = CryptoProviderType.JCA;
        CryptoAlgorithm algorithm = CryptoAlgorithm.AES_GCM_256;
        String keyAlias = "application-data";
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CryptoProvidersProperties {
        @Valid JcaProviderProperties jca = new JcaProviderProperties();
        @Valid JasyptProviderProperties jasypt = new JasyptProviderProperties();
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class JcaProviderProperties {
        boolean enabled = true;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class JasyptProviderProperties {
        boolean enabled;
        String algorithm = "PBEWithHmacSHA512AndAES_256";
        String keyAlias = "jasypt-property-key";
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class KeyCacheProperties {
        boolean enabled = true;
        Duration ttl = Duration.ofMinutes(5);
        @Min(1) int maximumSize = 100;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AuditProperties {
        boolean enabled = true;
        String loggerName = "AUDIT";
        boolean async;
        boolean publishSpringEvent = true;
        AuditFailureMode failMode = AuditFailureMode.FAIL_OPEN;
    }

    public enum AuditFailureMode {
        FAIL_OPEN, FAIL_CLOSED
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SecurityProperties {
        boolean rejectInsecureProductionConfig = true;
        boolean allowPlaintextSensitiveLog;
        boolean allowInlineKeys;
        boolean allowWeakCrypto;
        boolean emergencyAllowUnmaskedProduction;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MetricsProperties {
        boolean enabled = true;
    }
}
