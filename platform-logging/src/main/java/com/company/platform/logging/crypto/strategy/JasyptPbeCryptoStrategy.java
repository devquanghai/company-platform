package com.company.platform.logging.crypto.strategy;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public final class JasyptPbeCryptoStrategy implements CryptoStrategy {
    private static final int HASH_LENGTH = 32;
    private static final int MAC_LENGTH = 32;
    private static final byte[] MAC_LABEL =
        "platform-logging:jasypt:mac:v2".getBytes(StandardCharsets.UTF_8);
    private final String pbeAlgorithm;
    private final boolean allowLegacyDecrypt;

    public JasyptPbeCryptoStrategy(String pbeAlgorithm) {
        this(pbeAlgorithm, false);
    }

    public JasyptPbeCryptoStrategy(String pbeAlgorithm, boolean allowLegacyDecrypt) {
        this.pbeAlgorithm = pbeAlgorithm;
        this.allowLegacyDecrypt = allowLegacyDecrypt;
    }

    @Override public CryptoProviderType provider() { return CryptoProviderType.JASYPT; }
    @Override public CryptoAlgorithm algorithm() { return CryptoAlgorithm.PBE; }

    @Override
    public byte[] encrypt(byte[] plaintext, CryptoContext context) {
        return encryptResult(plaintext, context).getCiphertext();
    }

    @Override
    public CryptoResult encryptResult(byte[] plaintext, CryptoContext context) {
        byte[] bound = bind(context.authenticatedData("DIRECT"), plaintext);
        try {
            byte[] ciphertext = invoke("encrypt", bound, context);
            return CryptoResult.builder().mode("DIRECT").ciphertext(ciphertext)
                .authenticationTag(authenticationTag(context, ciphertext)).build();
        } finally {
            Arrays.fill(bound, (byte) 0);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
        if (context.getEnvelope() == null) {
            throw failure("PBE cipher envelope is required");
        }
        boolean legacy = "v1".equals(context.getEnvelope().getFormatVersion());
        if (legacy && !allowLegacyDecrypt) {
            throw failure("Legacy PBE decryption is disabled");
        }
        if (!legacy && (context.getEnvelope().getAuthenticationTag().length != MAC_LENGTH
            || !MessageDigest.isEqual(
                context.getEnvelope().getAuthenticationTag(),
                authenticationTag(context, ciphertext)))) {
            throw failure("PBE ciphertext authentication failed");
        }
        byte[] bound = invoke("decrypt", ciphertext, context);
        try {
            byte[] expected = hash(context.authenticatedData("DIRECT"));
            if (bound.length < HASH_LENGTH || !MessageDigest.isEqual(
                expected, Arrays.copyOf(bound, HASH_LENGTH))) {
                throw failure("PBE envelope metadata authentication failed");
            }
            return Arrays.copyOfRange(bound, HASH_LENGTH, bound.length);
        } finally {
            Arrays.fill(bound, (byte) 0);
        }
    }

    private static byte[] authenticationTag(CryptoContext context, byte[] ciphertext) {
        byte[] rootKey = context.getKeyMaterial().key().getEncoded();
        byte[] macKey = null;
        try {
            if (rootKey == null || rootKey.length < 16) {
                throw failure("PBE key material is invalid");
            }
            Mac derivation = Mac.getInstance("HmacSHA256");
            derivation.init(new SecretKeySpec(rootKey, "HmacSHA256"));
            macKey = derivation.doFinal(MAC_LABEL);
            Mac authentication = Mac.getInstance("HmacSHA256");
            authentication.init(new SecretKeySpec(macKey, "HmacSHA256"));
            byte[] aad = context.authenticatedData("DIRECT");
            authentication.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(aad.length).array());
            authentication.update(aad);
            authentication.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(ciphertext.length).array());
            authentication.update(ciphertext);
            return authentication.doFinal();
        } catch (PlatformCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("PBE authentication failed");
        } finally {
            if (rootKey != null) {
                Arrays.fill(rootKey, (byte) 0);
            }
            if (macKey != null) {
                Arrays.fill(macKey, (byte) 0);
            }
        }
    }

    private byte[] invoke(String operation, byte[] input, CryptoContext context) {
        byte[] key = context.getKeyMaterial().key().getEncoded();
        if (key == null || key.length < 16) {
            throw failure("PBE key material is invalid");
        }
        char[] password = Base64.getEncoder().encodeToString(key).toCharArray();
        Arrays.fill(key, (byte) 0);
        try {
            Class<?> type = Class.forName(
                "org.jasypt.encryption.pbe.StandardPBEByteEncryptor");
            Object encryptor = type.getConstructor().newInstance();
            type.getMethod("setAlgorithm", String.class).invoke(encryptor, pbeAlgorithm);
            configureIvGenerator(type, encryptor);
            type.getMethod("setPasswordCharArray", char[].class)
                .invoke(encryptor, (Object) password);
            Method method = type.getMethod(operation, byte[].class);
            return (byte[]) method.invoke(encryptor, (Object) input);
        } catch (Exception exception) {
            throw failure("Jasypt PBE operation failed");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void configureIvGenerator(Class<?> encryptorType, Object encryptor)
        throws ReflectiveOperationException {
        if (!pbeAlgorithm.toUpperCase(java.util.Locale.ROOT).contains("AES")) {
            return;
        }
        Class<?> ivGenerator = Class.forName("org.jasypt.iv.IvGenerator");
        Object randomIv = Class.forName("org.jasypt.iv.RandomIvGenerator")
            .getConstructor().newInstance();
        encryptorType.getMethod("setIvGenerator", ivGenerator)
            .invoke(encryptor, randomIv);
    }

    private static byte[] bind(byte[] aad, byte[] plaintext) {
        byte[] hash = hash(aad);
        byte[] result = Arrays.copyOf(hash, hash.length + plaintext.length);
        System.arraycopy(plaintext, 0, result, hash.length, plaintext.length);
        return result;
    }

    private static byte[] hash(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw failure("PBE metadata binding failed");
        }
    }

    private static PlatformCryptoException failure(String detail) {
        return new PlatformCryptoException("PLATFORM.CRYPTO.JASYPT", detail);
    }
}
