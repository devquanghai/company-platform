package com.company.platform.logging.crypto.envelope;

import com.company.platform.logging.domain.model.CipherEnvelope;

public interface CipherEnvelopeCodec {
    String encode(CipherEnvelope envelope);
    CipherEnvelope decode(String encoded);
}
