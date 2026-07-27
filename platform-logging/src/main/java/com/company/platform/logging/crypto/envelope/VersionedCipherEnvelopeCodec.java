package com.company.platform.logging.crypto.envelope;

import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public final class VersionedCipherEnvelopeCodec implements CipherEnvelopeCodec {
    private static final String PREFIX = "ENC[";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern MODE = Pattern.compile("DIRECT|HYBRID");
    private final int maxEnvelopeLength;

    public VersionedCipherEnvelopeCodec(int maxEnvelopeLength) {
        this.maxEnvelopeLength = maxEnvelopeLength;
    }

    @Override
    public String encode(CipherEnvelope envelope) {
        validateText(envelope.getKeyAlias(), "key alias");
        validateText(envelope.getKeyVersion(), "key version");
        if (!MODE.matcher(envelope.getMode()).matches()) {
            fail("unsupported cipher mode");
        }
        String encoded = PREFIX + String.join(":",
            envelope.getFormatVersion(),
            envelope.getProvider().name(),
            envelope.getAlgorithm().name(),
            text(envelope.getKeyAlias()),
            text(envelope.getKeyVersion()),
            envelope.getMode(),
            bytes(envelope.getNonce()),
            bytes(envelope.getWrappedKey()),
            bytes(envelope.getCiphertext()),
            bytes(envelope.getAuthenticationTag())) + "]";
        if (encoded.length() > maxEnvelopeLength) {
            fail("oversized cipher envelope");
        }
        return encoded;
    }

    @Override
    public CipherEnvelope decode(String encoded) {
        if (encoded == null || encoded.length() > maxEnvelopeLength
            || !encoded.startsWith(PREFIX) || !encoded.endsWith("]")) {
            fail("malformed or oversized cipher envelope");
        }
        String[] parts = encoded.substring(PREFIX.length(), encoded.length() - 1)
            .split(":", -1);
        if (parts.length != 10 || !"v1".equals(parts[0])) {
            fail("unsupported cipher envelope version");
        }
        try {
            CryptoProviderType provider = CryptoProviderType.valueOf(parts[1]);
            CryptoAlgorithm algorithm = CryptoAlgorithm.valueOf(parts[2]);
            String alias = decodeText(parts[3]);
            String version = decodeText(parts[4]);
            validateText(alias, "key alias");
            validateText(version, "key version");
            if (!MODE.matcher(parts[5]).matches()) {
                fail("unsupported cipher mode");
            }
            byte[] nonce = decodeBytes(parts[6]);
            byte[] wrapped = decodeBytes(parts[7]);
            byte[] ciphertext = decodeBytes(parts[8]);
            byte[] tag = decodeBytes(parts[9]);
            if (ciphertext.length == 0 || ciphertext.length > maxEnvelopeLength
                || nonce.length > 64 || tag.length > 64 || wrapped.length > 16_384) {
                fail("invalid cipher envelope field size");
            }
            return CipherEnvelope.builder().formatVersion(parts[0])
                .provider(provider).algorithm(algorithm).keyAlias(alias)
                .keyVersion(version).mode(parts[5]).nonce(nonce)
                .wrappedKey(wrapped).ciphertext(ciphertext)
                .authenticationTag(tag).build();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            fail("invalid cipher envelope");
            return null;
        }
    }

    private static String text(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytes(byte[] value) {
        return value.length == 0 ? "-"
            : Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String decodeText(String value) throws CharacterCodingException {
        byte[] decoded = decodeBytes(value);
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(decoded)).toString();
    }

    private static byte[] decodeBytes(String value) {
        if ("-".equals(value)) {
            return new byte[0];
        }
        if (value == null || value.isEmpty() || value.length() > 1_500_000) {
            fail("invalid cipher envelope encoding");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    private static void validateText(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            fail("invalid " + field);
        }
    }

    private static void fail(String detail) {
        throw new PlatformCryptoException("PLATFORM.CRYPTO.ENVELOPE", detail);
    }
}
