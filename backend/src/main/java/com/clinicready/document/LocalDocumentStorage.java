package com.clinicready.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stores documents as files under a root directory (a Docker volume in compose). */
@Component
class LocalDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);

    private final Path root;

    LocalDocumentStorage(DocumentProperties properties) {
        this.root = properties.storagePath().toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String key, InputStream content, long maxBytes) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        // Write to a temp file in the same directory, then move: readers never see a partial file.
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            MessageDigest sha256 = newDigest();
            long total = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new FileTooLargeException();
                    }
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(total, HexFormat.of().formatHex(sha256.digest()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException e) {
            // Never include the key's owner details; the key is only ids.
            log.warn("Could not delete stored document {}", key, e);
        }
    }

    /** Resolves a key under the root and refuses anything that would escape it. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
