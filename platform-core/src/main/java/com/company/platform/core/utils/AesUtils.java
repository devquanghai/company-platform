package com.company.platform.core.utils;

import com.company.platform.core.exception.PlatformInfrastructureException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@UtilityClass
public class AesUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 32;
    private static final int TAG_LENGTH_BIT = 128;  // 128-bit auth tag
    private static final String AES = "AES";
    @SuppressWarnings("java:S5542")
    private static final String RSA_TRANSFORMATION =
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String LEGACY_RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int LEGACY_IV_LENGTH = 16;

    private static final String LOG_ENCRYPT_ERROR = "AES encrypt failed";
    private static final String LOG_DECRYPT_ERROR = "AES decrypt failed";

    public SecretKey generateKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must contain 128, 192, or 256 bits");
        }
        return new SecretKeySpec(
                keyBytes,
                AES
        );
    }

    public SecretKey getKeyFromPassword(String password, String salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            210_000,
            256
        );
        try {
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), AES);
        } finally {
            spec.clearPassword();
        }
    }

    public byte[] generateAesKey() {

        byte[] aesKey =
            new byte[AES_KEY_SIZE];

        SECURE_RANDOM.nextBytes(aesKey);

        return aesKey;
    }

    public byte[] generateIv() {

        byte[] iv = new byte[IV_LENGTH];

        SECURE_RANDOM.nextBytes(iv);

        return iv;
    }

    public GCMParameterSpec buildGcmSpec(
            byte[] iv
    ) {
        return new GCMParameterSpec(
                TAG_LENGTH_BIT,
                iv
        );
    }

    public String encrypt(String input, String secretKey) {

        try {

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            SecretKey key = AesUtils.generateKey(secretKey);

            byte[] iv = generateIv();

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    buildGcmSpec(iv)
            );

            byte[] cipherText =
                    cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);

        } catch (Exception ex) {

            log.error(LOG_ENCRYPT_ERROR, ex);

            throw failure(LOG_ENCRYPT_ERROR, ex);
        }
    }

    public String decrypt(String cipherText, String secretKey) {

        try {

            SecretKey key = AesUtils.generateKey(secretKey);

            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }
            byte[] plainText;
            try {
                plainText = decryptGcm(payload, key, IV_LENGTH);
            } catch (GeneralSecurityException currentFormatFailure) {
                if (payload.length <= LEGACY_IV_LENGTH) {
                    throw currentFormatFailure;
                }
                plainText = decryptGcm(payload, key, LEGACY_IV_LENGTH);
            }

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception ex) {

            log.error(LOG_DECRYPT_ERROR, ex);

            throw failure(LOG_DECRYPT_ERROR, ex);
        }
    }

    @SuppressWarnings("java:S5542")
    public byte[] encryptAesKey(
            byte[] aesKey,
            PublicKey publicKey
    ) {
        try {

            Cipher cipher =
                    Cipher.getInstance(
                            RSA_TRANSFORMATION
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    publicKey
            );

            return cipher.doFinal(
                    aesKey
            );
        } catch (Exception ex) {

            log.error(
                    "RSA AES-key encryption failed",
                    ex
            );

            throw failure("RSA AES-key encryption failed", ex);
        }
    }

    @SuppressWarnings("java:S5542")
    public byte[] decryptAesKey(
            byte[] encryptedAesKey,
            PrivateKey privateKey
    ) {
        try {

            try {
                return decryptRsaKey(encryptedAesKey, privateKey, RSA_TRANSFORMATION);
            } catch (GeneralSecurityException currentFormatFailure) {
                return decryptRsaKey(encryptedAesKey, privateKey, LEGACY_RSA_TRANSFORMATION);
            }
        } catch (Exception ex) {

            log.error(
                    LOG_DECRYPT_ERROR,
                    ex
            );

            throw failure(LOG_DECRYPT_ERROR, ex);
        }
    }

    public byte[] hybridEncrypt(byte[] plaintext, byte[] aesKey) {

        try {

            byte[] iv = generateIv();

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, AES),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv)
            );

            byte[] encrypted = cipher.doFinal(plaintext);

            byte[] result = new byte[iv.length + encrypted.length];

            System.arraycopy(
                    iv,
                    0,
                    result,
                    0,
                    iv.length
            );

            System.arraycopy(
                    encrypted,
                    0,
                    result,
                    iv.length,
                    encrypted.length
            );

            return result;

        } catch (Exception ex) {

            log.error("[AES] Hybrid encrypt failed", ex);

            throw failure(LOG_ENCRYPT_ERROR, ex);
        }
    }

    public byte[] hybridDecrypt(byte[] encryptedPayload, byte[] aesKey) {

        try {

            if (encryptedPayload.length <= IV_LENGTH) {
                throw new IllegalArgumentException(
                        "Invalid encrypted payload"
                );
            }

            SecretKeySpec key = new SecretKeySpec(aesKey, AES);
            try {
                return decryptGcm(encryptedPayload, key, IV_LENGTH);
            } catch (GeneralSecurityException currentFormatFailure) {
                if (encryptedPayload.length <= LEGACY_IV_LENGTH) {
                    throw currentFormatFailure;
                }
                return decryptGcm(encryptedPayload, key, LEGACY_IV_LENGTH);
            }

        } catch (Exception ex) {

            log.error("[AES] Hybrid decrypt failed", ex);

            throw failure(LOG_DECRYPT_ERROR, ex);
        }
    }

    private static PlatformInfrastructureException failure(String message, Throwable cause) {
        return new PlatformInfrastructureException("CORE.CRYPTO.AES", message, cause);
    }

    private static byte[] decryptGcm(byte[] payload, SecretKey key, int ivLength)
        throws GeneralSecurityException {
        byte[] iv = Arrays.copyOfRange(payload, 0, ivLength);
        byte[] encrypted = Arrays.copyOfRange(payload, ivLength, payload.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        return cipher.doFinal(encrypted);
    }

    private static byte[] decryptRsaKey(
        byte[] encryptedAesKey,
        PrivateKey privateKey,
        String transformation
    ) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedAesKey);
    }
}
