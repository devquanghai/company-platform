package com.company.platform.tool.archive.internal;

import com.company.platform.tool.api.UnsafeFileException;
import com.company.platform.tool.archive.api.ArchiveService;
import com.company.platform.tool.archive.model.ArchiveEntrySource;
import com.company.platform.tool.common.NonClosingOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SecureZipArchiveService implements ArchiveService {
    private static final int BUFFER_SIZE = 16 * 1024;

    @Override
    public void createZip(List<ArchiveEntrySource> entries, OutputStream output) throws IOException {
        if (entries == null || entries.size() > 10_000)
            throw new IllegalArgumentException("ZIP must contain at most 10,000 entries");
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            for (ArchiveEntrySource entry : List.copyOf(entries)) {
                String name = safeEntry(entry.name());
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate ZIP entry: " + name);
                zip.putNextEntry(new ZipEntry(name));
                try (InputStream source = entry.source().open()) {
                    source.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    @Override
    public List<Path> extractZip(InputStream input, Path destination, long maximumExpandedBytes, int maximumEntries) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new UnsafeFileException("Destination must not be a symbolic link");
        List<Path> extracted = new ArrayList<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (extracted.size() >= maximumEntries) throw new UnsafeFileException("ZIP entry limit exceeded");
                Path target = root.resolve(safeEntry(entry.getName())).normalize();
                if (!target.startsWith(root)) throw new UnsafeFileException("ZIP entry escapes destination");
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                    throw new UnsafeFileException("ZIP entry would overwrite a file");
                extracted.add(target);
                try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    for (int read; (read = zip.read(buffer)) >= 0; ) {
                        if (read == 0) continue;
                        total += read;
                        if (total > maximumExpandedBytes)
                            throw new UnsafeFileException("ZIP expanded size limit exceeded");
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            for (int index = extracted.size() - 1; index >= 0; index--) Files.deleteIfExists(extracted.get(index));
            throw exception;
        }
        return List.copyOf(extracted);
    }

    private static String safeEntry(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains(":") || normalized.indexOf('\0') >= 0)
            throw new UnsafeFileException("Unsafe ZIP entry name");
        return normalized;
    }
}
