package com.company.platform.logging.crypto.strategy;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CipherEnvelope;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoResult;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

public final class RsaOaepCryptoStrategy implements CryptoStrategy {
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final int HASH_LENGTH = 32;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override public CryptoProviderType provider() { return CryptoProviderType.JCA; }
    @Override public CryptoAlgorithm algorithm() { return CryptoAlgorithm.RSA_OAEP_SHA256; }

    @Override
    public byte[] encrypt(byte[] plaintext, CryptoContext context) {
        return encryptResult(plaintext, context).getCiphertext();
    }

    @Override
    public CryptoResult encryptResult(byte[] plaintext, CryptoContext context) {
        PublicKey key = publicKey(context.getKeyMaterial().key());
        int directBound = modulusBytes(key) - 2 * HASH_LENGTH - 2 - HASH_LENGTH;
        if (plaintext.length <= directBound) {
            byte[] bound = bind(context.authenticatedData("DIRECT"), plaintext);
            try {
                return CryptoResult.builder().mode("DIRECT")
                    .ciphertext(rsa(Cipher.ENCRYPT_MODE, key, bound)).build();
            } finally {
                Arrays.fill(bound, (byte) 0);
            }
        }
        return hybridEncrypt(plaintext, context, key);
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
        PrivateKey key = privateKey(context.getKeyMaterial().key());
        CipherEnvelope envelope = context.getEnvelope();
        String mode = envelope == null ? "DIRECT" : envelope.getMode();
        if ("DIRECT".equals(mode)) {
            byte[] bound = rsa(Cipher.DECRYPT_MODE, key, ciphertext);
            try {
                return unbind(context.authenticatedData("DIRECT"), bound);
            } finally {
                Arrays.fill(bound, (byte) 0);
            }
        }
        if (!"HYBRID".equals(mode) || envelope == null) {
            throw failure("unsupported RSA cipher mode");
        }
        return hybridDecrypt(context, key, envelope);
    }

    private static CryptoResult hybridEncrypt(
        byte[] plaintext, CryptoContext context, PublicKey rsaKey
    ) {
        byte[] dataKey = null;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, RANDOM);
            SecretKey key = generator.generateKey();
            dataKey = key.getEncoded();
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nonce));
            aes.updateAAD(context.authenticatedData("HYBRID"));
            byte[] encrypted = aes.doFinal(plaintext);
            int split = encrypted.length - TAG_LENGTH;
            return CryptoResult.builder().mode("HYBRID").nonce(nonce)
                .wrappedKey(rsa(Cipher.ENCRYPT_MODE, rsaKey, dataKey))
                .ciphertext(Arrays.copyOf(encrypted, split))
                .authenticationTag(Arrays.copyOfRange(encrypted, split, encrypted.length))
                .build();
        } catch (PlatformCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("hybrid encryption failed");
        } finally {
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
        }
    }

    private static byte[] hybridDecrypt(
        CryptoContext context, PrivateKey rsaKey, CipherEnvelope envelope
    ) {
        byte[] dataKey = rsa(Cipher.DECRYPT_MODE, rsaKey, envelope.getWrappedKey());
        try {
            if (dataKey.length != 32 || envelope.getNonce().length != NONCE_LENGTH
                || envelope.getAuthenticationTag().length != TAG_LENGTH) {
                throw failure("hybrid cipher payload is malformed");
            }
            byte[] encrypted = Arrays.copyOf(envelope.getCiphertext(),
                envelope.getCiphertext().length + TAG_LENGTH);
            System.arraycopy(envelope.getAuthenticationTag(), 0, encrypted,
                envelope.getCiphertext().length, TAG_LENGTH);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(dataKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, envelope.getNonce()));
            aes.updateAAD(context.authenticatedData("HYBRID"));
            return aes.doFinal(encrypted);
        } catch (PlatformCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("hybrid authentication or decryption failed");
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    private static byte[] rsa(int mode, Key key, byte[] value) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(mode, key, OAEP);
            return cipher.doFinal(value);
        } catch (Exception exception) {
            throw failure("RSA-OAEP operation failed");
        }
    }

    private static byte[] bind(byte[] aad, byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(aad);
            byte[] result = Arrays.copyOf(hash, hash.length + value.length);
            System.arraycopy(value, 0, result, hash.length, value.length);
            return result;
        } catch (Exception exception) {
            throw failure("metadata binding failed");
        }
    }

    private static byte[] unbind(byte[] aad, byte[] value) {
        if (value.length < HASH_LENGTH) {
            throw failure("RSA payload is malformed");
        }
        byte[] expected = bind(aad, new byte[0]);
        byte[] actual = Arrays.copyOf(value, HASH_LENGTH);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw failure("RSA envelope metadata authentication failed");
        }
        return Arrays.copyOfRange(value, HASH_LENGTH, value.length);
    }

    private static PublicKey publicKey(Key key) {
        validateRsa(key);
        if (!(key instanceof PublicKey publicKey)) {
            throw failure("RSA encryption requires a public key");
        }
        return publicKey;
    }

    private static PrivateKey privateKey(Key key) {
        validateRsa(key);
        if (!(key instanceof PrivateKey privateKey)) {
            throw failure("RSA decryption requires a private key");
        }
        return privateKey;
    }

    private static void validateRsa(Key key) {
        if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048
            || !"RSA".equalsIgnoreCase(key.getAlgorithm())) {
            throw failure("RSA key must be at least 2048 bits");
        }
    }

    private static int modulusBytes(Key key) {
        return ((((RSAKey) key).getModulus().bitLength()) + 7) / 8;
    }

    private static PlatformCryptoException failure(String detail) {
        return new PlatformCryptoException("PLATFORM.CRYPTO.RSA_OAEP", detail);
    }
}
