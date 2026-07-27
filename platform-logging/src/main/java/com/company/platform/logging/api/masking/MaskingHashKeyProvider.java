package com.company.platform.logging.api.masking;

public interface MaskingHashKeyProvider {
    byte[] resolveHmacKey(String alias);
}
