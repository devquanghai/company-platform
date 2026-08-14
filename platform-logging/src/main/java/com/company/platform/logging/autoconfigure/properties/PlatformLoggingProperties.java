package com.company.platform.logging.autoconfigure.properties;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.MaskingMatchType;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

    String environment = "unknown";
    @Valid ContextProperties context = new ContextProperties();
    @Valid MethodLoggingProperties methodLogging = new MethodLoggingProperties();
    @Valid MaskingProperties masking = new MaskingProperties();
    @Valid CryptoProperties crypto = new CryptoProperties();
    @Valid AuditProperties audit = new AuditProperties();
    @Valid SecurityProperties security = new SecurityProperties();

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ContextProperties {
        UserIdMode userIdMode = UserIdMode.OMIT;
    }

    public enum UserIdMode {
        OMIT, MASK, HASH
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MethodLoggingProperties {
        boolean includeArgumentsByDefault;
        boolean includeResultByDefault;
        boolean includeDuration = true;
        boolean includeException = true;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MaskingProperties {
        boolean sanitizeControlCharacters = true;
        /** @deprecated retained as a traversal-safety budget, never used to truncate text. */
        @Deprecated @Min(1) int maxDepth = 10;
        /** @deprecated retained for binary configuration compatibility; text is not truncated. */
        @Deprecated @Min(1) int maxStringLength = 4096;
        /** @deprecated retained as a traversal-safety budget, never used for partial output. */
        @Deprecated @Min(1) int maxCollectionSize = 100;
        /** @deprecated retained as a traversal-safety budget, never used for partial output. */
        @Deprecated @Min(1) int maxMapSize = 100;
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
        @Valid JasyptProviderProperties jasypt = new JasyptProviderProperties();
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class JasyptProviderProperties {
        boolean enabled;
        String algorithm = "PBEWithHmacSHA512AndAES_256";
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class KeyCacheProperties {
        Duration ttl = Duration.ofMinutes(5);
        @Min(1) int maximumSize = 100;
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AuditProperties {
        boolean publishSpringEvent = true;
        AuditFailureMode failMode = AuditFailureMode.FAIL_OPEN;
    }

    public enum AuditFailureMode {
        FAIL_OPEN, FAIL_CLOSED
    }

    @Getter @Setter @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SecurityProperties {
        boolean allowWeakCrypto;
    }

}
