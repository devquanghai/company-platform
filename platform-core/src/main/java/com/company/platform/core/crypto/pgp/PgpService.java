package com.company.platform.core.crypto.pgp;

import java.io.InputStream;

public interface PgpService {

    InputStream encrypt(InputStream plainInputStream, InputStream publicKeyInputStream);

    InputStream decrypt(InputStream encryptedInputStream, InputStream privateKeyInputStream, String passphrase);
}
