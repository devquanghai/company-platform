package com.company.platform.logging.crypto.envelope;

import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeBranchCoverageTest {

    @Test
    void encodeCoversEmptyAndPopulatedFieldsAndRejectsEveryBoundary() {
        VersionedCipherEnvelopeCodec codec = new VersionedCipherEnvelopeCodec(10_000);
        String empty = codec.encode(envelope("alias", "v1", "DIRECT",
            new byte[0], new byte[0], new byte[]{1}, new byte[0]));
        assertThat(empty).contains(":-:-:");
        assertThat(codec.decode(empty).getWrappedKey()).isEmpty();

        assertRejected(() -> codec.encode(envelope("bad alias", "v1", "DIRECT",
            new byte[0], new byte[0], new byte[]{1}, new byte[0])));
        assertRejected(() -> codec.encode(envelope("alias", "", "DIRECT",
            new byte[0], new byte[0], new byte[]{1}, new byte[0])));
        assertRejected(() -> codec.encode(envelope("alias", "v1", "UNKNOWN",
            new byte[0], new byte[0], new byte[]{1}, new byte[0])));
        VersionedCipherEnvelopeCodec tiny = new VersionedCipherEnvelopeCodec(20);
        assertRejected(() -> tiny.encode(envelope("alias", "v1", "DIRECT",
            new byte[0], new byte[0], new byte[]{1}, new byte[0])));
    }

    @Test
    void decodeRejectsMalformedShapeVersionProviderAlgorithmAndEncoding() {
        VersionedCipherEnvelopeCodec codec = new VersionedCipherEnvelopeCodec(50_000);
        assertRejected(() -> codec.decode(null));
        assertRejected(() -> codec.decode("x".repeat(50_001)));
        assertRejected(() -> codec.decode("plain"));
        assertRejected(() -> codec.decode("ENC[v1:JCA]"));
        assertRejected(() -> codec.decode(valid().replace("ENC[v1:", "ENC[v2:")));
        assertRejected(() -> codec.decode(valid().replace(":JCA:", ":NOPE:")));
        assertRejected(() -> codec.decode(valid().replace(
            ":AES_GCM_256:", ":NO_ALGORITHM:")));

        String[] parts = parts(valid());
        parts[3] = "@@@";
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[3] = b64("bad alias");
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[4] = b64("");
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[5] = "UNKNOWN";
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[6] = "";
        assertRejectedDecode(codec, join(parts));
    }

    @Test
    void decodeRejectsEveryFieldSizeLimitAndInvalidUtf8() {
        VersionedCipherEnvelopeCodec codec = new VersionedCipherEnvelopeCodec(100_000);
        String[] parts = parts(valid());
        parts[3] = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[]{(byte) 0xC3, 0x28});
        assertRejectedDecode(codec, join(parts));

        parts = parts(valid());
        parts[8] = "-";
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[6] = b64(new byte[65]);
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[9] = b64(new byte[65]);
        assertRejectedDecode(codec, join(parts));
        parts = parts(valid());
        parts[7] = b64(new byte[16_385]);
        assertRejectedDecode(codec, join(parts));

        VersionedCipherEnvelopeCodec huge = new VersionedCipherEnvelopeCodec(2_000_000);
        parts = parts(valid());
        parts[8] = "A".repeat(1_500_001);
        String oversizedField = join(parts);
        assertRejected(() -> huge.decode(oversizedField));
    }

    private static String valid() {
        return new VersionedCipherEnvelopeCodec(10_000).encode(
            envelope("alias", "v1", "HYBRID",
                new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}));
    }

    private static CipherEnvelope envelope(
        String alias, String version, String mode, byte[] nonce,
        byte[] wrapped, byte[] ciphertext, byte[] tag
    ) {
        return new CipherEnvelope("v1", CryptoProviderType.JCA,
            CryptoAlgorithm.AES_GCM_256, alias, version, mode,
            nonce, wrapped, ciphertext, tag);
    }

    private static String[] parts(String encoded) {
        return encoded.substring(4, encoded.length() - 1).split(":", -1);
    }

    private static String join(String[] parts) {
        return "ENC[" + String.join(":", parts) + "]";
    }

    private static String b64(String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(PlatformCryptoException.class);
    }

    private static void assertRejectedDecode(
        VersionedCipherEnvelopeCodec codec, String encoded
    ) {
        assertRejected(() -> codec.decode(encoded));
    }
}
