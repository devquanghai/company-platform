package com.company.platform.tool.file.internal;

import com.company.platform.tool.file.api.DigestService;
import com.company.platform.tool.file.model.DigestAlgorithm;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class JcaDigestService implements DigestService {
    @Override
    public String digest(InputStream input, DigestAlgorithm algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.jcaName());
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required digest algorithm is unavailable", exception);
        }
    }
}
