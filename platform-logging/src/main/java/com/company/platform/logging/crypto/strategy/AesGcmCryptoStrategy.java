package com.company.platform.logging.crypto.strategy;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoResult;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AesGcmCryptoStrategy implements CryptoStrategy {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override public CryptoProviderType provider() { return CryptoProviderType.JCA; }
    @Override public CryptoAlgorithm algorithm() { return CryptoAlgorithm.AES_GCM_256; }

    @Override
    public byte[] encrypt(byte[] plaintext, CryptoContext context) {
        CryptoResult result = encryptResult(plaintext, context);
        byte[] joined = new byte[result.getNonce().length
            + result.getCiphertext().length + result.getAuthenticationTag().length];
        int offset = 0;
        System.arraycopy(result.getNonce(), 0, joined, offset, result.getNonce().length);
        offset += result.getNonce().length;
        System.arraycopy(result.getCiphertext(), 0, joined, offset, result.getCiphertext().length);
        offset += result.getCiphertext().length;
        System.arraycopy(result.getAuthenticationTag(), 0, joined, offset,
            result.getAuthenticationTag().length);
        return joined;
    }

    @Override
    public CryptoResult encryptResult(byte[] plaintext, CryptoContext context) {
        validateKey(context);
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, context.getKeyMaterial().key(),
                new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nonce));
            cipher.updateAAD(context.authenticatedData("DIRECT"));
            byte[] result = cipher.doFinal(plaintext);
            int split = result.length - TAG_LENGTH;
            return CryptoResult.builder().mode("DIRECT").nonce(nonce)
                .ciphertext(Arrays.copyOf(result, split))
                .authenticationTag(Arrays.copyOfRange(result, split, result.length))
                .build();
        } catch (Exception exception) {
            throw failure("encryption failed");
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
        validateKey(context);
        CipherEnvelope envelope = context.getEnvelope();
        byte[] nonce;
        byte[] encrypted;
        byte[] tag;
        if (envelope == null) {
            if (ciphertext.length <= NONCE_LENGTH + TAG_LENGTH) {
                throw failure("cipher payload is malformed");
            }
            nonce = Arrays.copyOf(ciphertext, NONCE_LENGTH);
            tag = Arrays.copyOfRange(ciphertext, ciphertext.length - TAG_LENGTH,
                ciphertext.length);
            encrypted = Arrays.copyOfRange(ciphertext, NONCE_LENGTH,
                ciphertext.length - TAG_LENGTH);
        } else {
            nonce = envelope.getNonce();
            encrypted = envelope.getCiphertext();
            tag = envelope.getAuthenticationTag();
        }
        if (nonce.length != NONCE_LENGTH || tag.length != TAG_LENGTH) {
            throw failure("cipher payload is malformed");
        }
        try {
            byte[] joined = Arrays.copyOf(encrypted, encrypted.length + tag.length);
            System.arraycopy(tag, 0, joined, encrypted.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, context.getKeyMaterial().key(),
                new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nonce));
            cipher.updateAAD(context.authenticatedData("DIRECT"));
            return cipher.doFinal(joined);
        } catch (Exception exception) {
            throw failure("authentication or decryption failed");
        }
    }

    private static void validateKey(CryptoContext context) {
        if (!(context.getKeyMaterial().key() instanceof SecretKey key)
            || !"AES".equalsIgnoreCase(key.getAlgorithm())
            || key.getEncoded() == null || key.getEncoded().length != 32
            || context.getKeyMaterial().algorithm() != CryptoAlgorithm.AES_GCM_256) {
            throw failure("AES-GCM requires a 256-bit AES key");
        }
    }

    private static PlatformCryptoException failure(String detail) {
        return new PlatformCryptoException("PLATFORM.CRYPTO.AES_GCM", detail);
    }
}
