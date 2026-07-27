package com.company.platform.logging.crypto.strategy;

import com.company.platform.logging.api.crypto.CryptoStrategy;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoContext;
import com.company.platform.logging.domain.model.CryptoProviderType;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public final class JasyptPbeCryptoStrategy implements CryptoStrategy {
    private static final int HASH_LENGTH = 32;
    private final String pbeAlgorithm;

    public JasyptPbeCryptoStrategy(String pbeAlgorithm) {
        this.pbeAlgorithm = pbeAlgorithm;
    }

    @Override public CryptoProviderType provider() { return CryptoProviderType.JASYPT; }
    @Override public CryptoAlgorithm algorithm() { return CryptoAlgorithm.PBE; }

    @Override
    public byte[] encrypt(byte[] plaintext, CryptoContext context) {
        byte[] bound = bind(context.authenticatedData("DIRECT"), plaintext);
        try {
            return invoke("encrypt", bound, context);
        } finally {
            Arrays.fill(bound, (byte) 0);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, CryptoContext context) {
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
