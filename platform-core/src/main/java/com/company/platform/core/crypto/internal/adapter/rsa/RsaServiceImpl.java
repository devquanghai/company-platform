package com.company.platform.core.crypto.internal.adapter.rsa;

import com.company.platform.core.crypto.rsa.RsaService;
import com.company.platform.core.utils.AesUtils;
import com.company.platform.core.exception.PlatformInfrastructureException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

@Slf4j
public final class RsaServiceImpl implements RsaService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String PROVIDER = "SunRsaSign";

    /**
     * Encrypt data using Public Key (PEM or from Certificate)
     */
    public String encrypt(String plainText, PublicKey publicKey) {
        try {
            validateKeySize(publicKey);

            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.error("RSA encryption failed: {}", e.getMessage(), e);
            throw failure("RSA encryption failed", e);
        }
    }

    @Override
    public String encryptMixRsaAes(
        String plainText,
        PublicKey publicKey
    ) {

        byte[] aesKey =
            AesUtils.generateAesKey();

        byte[] encryptedAesKey =
            AesUtils.encryptAesKey(
                aesKey,
                publicKey
            );

        byte[] encryptedPayload =
            AesUtils.hybridEncrypt(
                plainText.getBytes(
                    StandardCharsets.UTF_8
                ),
                aesKey
            );

        byte[] result =
            new byte[
                encryptedAesKey.length
                    + encryptedPayload.length
                ];

        System.arraycopy(
            encryptedAesKey,
            0,
            result,
            0,
            encryptedAesKey.length
        );

        System.arraycopy(
            encryptedPayload,
            0,
            result,
            encryptedAesKey.length,
            encryptedPayload.length
        );

        return Base64.getEncoder()
            .encodeToString(result);
    }

    /**
     * Decrypt data using Private Key
     */
    public String decrypt(String cipherTextBase64, PrivateKey privateKey) {
        try {
            validateKeySize(privateKey);

            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("RSA decryption failed: {}", e.getMessage(), e);
            throw failure("RSA decryption failed", e);
        }
    }

    @Override
    public String decryptMixRsaAes(
        String encryptedData,
        PrivateKey privateKey
    ) {

        byte[] combinedData =
            Base64.getDecoder()
                .decode(encryptedData);

        int rsaBlockSize =
            getRsaBlockSize(
                privateKey
            );

        if (combinedData.length <= rsaBlockSize) {

            throw new IllegalArgumentException(
                "Invalid hybrid payload"
            );
        }

        byte[] encryptedAesKey =
            new byte[rsaBlockSize];

        System.arraycopy(
            combinedData,
            0,
            encryptedAesKey,
            0,
            rsaBlockSize
        );

        byte[] aesKey =
            AesUtils.decryptAesKey(
                encryptedAesKey,
                privateKey
            );

        byte[] encryptedPayload =
            new byte[
                combinedData.length
                    - rsaBlockSize
                ];

        System.arraycopy(
            combinedData,
            rsaBlockSize,
            encryptedPayload,
            0,
            encryptedPayload.length
        );

        byte[] plainBytes =
            AesUtils.hybridDecrypt(
                encryptedPayload,
                aesKey
            );

        return new String(
            plainBytes,
            StandardCharsets.UTF_8
        );
    }

    /**
     * Sign data with Private Key
     */
    public String sign(String data, PrivateKey privateKey) {
        try {
            validateKeySize(privateKey);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(signature.sign());

        } catch (Exception e) {
            log.error("RSA sign failed: {}", e.getMessage(), e);
            throw failure("RSA signing failed", e);
        }
    }

    /**
     * Verify digital signature
     */
    public boolean verify(String data, String signatureBase64, PublicKey publicKey) {
        try {
            validateKeySize(publicKey);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));

        } catch (Exception e) {
            log.error("RSA verify failed: {}", e.getMessage(), e);
            throw failure("RSA verify failed", e);
        }
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private static void validateKeySize(Key key) {
        if (key instanceof RSAKey rsaKey) {
            int keySize = rsaKey.getModulus().bitLength();
            if (keySize < 2048) {
                throw failure("Key size must be >= 2048 bits", null);
            }
        }
    }

    private int getRsaBlockSize(
        PrivateKey privateKey
    ) {

        if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {

            throw new IllegalArgumentException(
                "Private key must be RSA private key"
            );
        }

        return rsaPrivateKey
            .getModulus()
            .bitLength()
            / Byte.SIZE;
    }

    private static PlatformInfrastructureException failure(String message, Throwable cause) {
        return new PlatformInfrastructureException("CORE.CRYPTO.RSA", message, cause);
    }
}
