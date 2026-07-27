package com.company.platform.core.crypto.rsa;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface RsaService {

    String encrypt(String plainText, PublicKey publicKey);

    String encryptMixRsaAes(String plainText, PublicKey publicKey);

    String decrypt(String cipherTextBase64, PrivateKey privateKey);

    String decryptMixRsaAes(String cipherTextBase64, PrivateKey privateKey);

    String sign(String data, PrivateKey privateKey);

    boolean verify(String data, String signatureBase64, PublicKey publicKey);
}
