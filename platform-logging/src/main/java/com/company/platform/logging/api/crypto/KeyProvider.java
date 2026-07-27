package com.company.platform.logging.api.crypto;

import com.company.platform.logging.domain.model.KeyMaterial;
import com.company.platform.logging.domain.model.KeyReference;

public interface KeyProvider {
    KeyMaterial resolveEncryptionKey(KeyReference reference);
    KeyMaterial resolveDecryptionKey(KeyReference reference);
}
