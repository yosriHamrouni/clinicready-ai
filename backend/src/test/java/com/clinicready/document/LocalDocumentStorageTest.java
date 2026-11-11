package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.clinicready.document.DocumentStorage.StoredFile;

class LocalDocumentStorageTest {

    private static final long MAX = 1024;

    @TempDir
    Path root;

    private LocalDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDocumentStorage(new DocumentProperties(root, DataSize.ofBytes(MAX)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long regularFiles() throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void storesBytesAndReturnsSizeAndSha256() throws Exception {
        byte[] bytes = "%PDF-1.4 hello".getBytes();

        StoredFile stored = storage.store("clinic/doc1", new ByteArrayInputStream(bytes), MAX);

        assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
        assertThat(stored.sha256()).isEqualTo(sha256(bytes)).matches("[0-9a-f]{64}");
        assertThat(storage.open("clinic/doc1").readAllBytes()).isEqualTo(bytes);
        assertThat(regularFiles()).isEqualTo(1); // no temp file left behind
    }

    @Test
    void refusesFilesOverTheLimitAndLeavesNothingBehind() throws Exception {
        byte[] tooBig = new byte[(int) MAX + 1];

        assertThatThrownBy(() -> storage.store("clinic/doc2", new ByteArrayInputStream(tooBig), MAX))
                .isInstanceOf(FileTooLargeException.class);

        assertThat(regularFiles()).isZero();
        assertThatThrownBy(() -> storage.open("clinic/doc2")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void acceptsAFileExactlyAtTheLimit() throws Exception {
        StoredFile stored = storage.store("clinic/doc3", new ByteArrayInputStream(new byte[(int) MAX]), MAX);

        assertThat(stored.sizeBytes()).isEqualTo(MAX);
    }

    @Test
    void refusesKeysThatEscapeTheRoot() {
        for (String key : new String[] {"../outside", "clinic/../../outside", "/etc/passwd", "", "."}) {
            assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream(new byte[1]), MAX))
                    .as(key).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void deleteRemovesTheFileAndIsIdempotent() throws Exception {
        storage.store("clinic/doc4", new ByteArrayInputStream(new byte[] {1}), MAX);

        storage.delete("clinic/doc4");
        assertThatCode(() -> storage.delete("clinic/doc4")).doesNotThrowAnyException();

        assertThat(regularFiles()).isZero();
    }
}
