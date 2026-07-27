package com.company.platform.logging.masking.strategy;

import com.company.platform.logging.api.masking.MaskingHashKeyProvider;
import com.company.platform.logging.api.masking.MaskingStrategy;
import com.company.platform.logging.domain.model.MaskingContext;
import com.company.platform.logging.domain.model.MaskingResult;
import com.company.platform.logging.domain.model.MaskingType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class HashMaskingStrategy implements MaskingStrategy {
    private final MaskingHashKeyProvider keys;
    private final String keyAlias;

    public HashMaskingStrategy(MaskingHashKeyProvider keys, String keyAlias) {
        this.keys = keys;
        this.keyAlias = keyAlias;
    }

    @Override public MaskingType type() { return MaskingType.HASH; }

    @Override
    public MaskingResult mask(String value, MaskingContext context) {
        if (value == null) {
            return MaskingResult.unchanged(null);
        }
        try {
            byte[] digest;
            String prefix;
            if (keys != null && keyAlias != null && !keyAlias.isBlank()) {
                byte[] key = keys.resolveHmacKey(keyAlias);
                try {
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(key, "HmacSHA256"));
                    digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
                    prefix = "hmac-sha256:";
                } finally {
                    java.util.Arrays.fill(key, (byte) 0);
                }
            } else {
                digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
                prefix = "sha256:";
            }
            return MaskingResult.masked(prefix + HexFormat.of().formatHex(digest));
        } catch (Exception exception) {
            return MaskingResult.masked("***");
        }
    }
}
