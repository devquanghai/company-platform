package com.company.platform.tool.file.internal;

import com.company.platform.tool.api.UnsafeFileException;
import com.company.platform.tool.file.api.FileInspectionService;
import com.company.platform.tool.file.model.FileInspection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.apache.tika.Tika;

public final class TikaFileInspectionService implements FileInspectionService {
    private final Tika tika = new Tika();

    @Override
    public FileInspection inspect(InputStream input, String claimedFilename, long maximumBytes) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(64 * 1024);
        String type = tika.detect(buffered, safeFilename(claimedFilename));
        buffered.reset();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long size = 0;
        byte[] bytes = new byte[16 * 1024];
        for (int read; (read = buffered.read(bytes)) >= 0; ) {
            if (read == 0) continue;
            size += read;
            if (size > maximumBytes) throw new UnsafeFileException("File size limit exceeded");
            digest.update(bytes, 0, read);
        }
        return new FileInspection(type, extension(claimedFilename), size, HexFormat.of().formatHex(digest.digest()));
    }

    private static String safeFilename(String name) {
        return name == null ? "upload" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String extension(String name) {
        String safe = safeFilename(name).toLowerCase(Locale.ROOT);
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1);
    }
}
