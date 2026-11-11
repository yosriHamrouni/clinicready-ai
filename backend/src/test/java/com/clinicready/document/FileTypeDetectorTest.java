package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.clinicready.document.FileTypeDetector.FileType;

class FileTypeDetectorTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static java.util.Optional<FileType> detect(byte[] bytes) {
        return FileTypeDetector.detect(bytes, Math.min(bytes.length, FileTypeDetector.HEADER_BYTES));
    }

    @Test
    void recognisesPdfJpegAndPngByContent() {
        assertThat(detect(PDF)).contains(FileType.PDF);
        assertThat(detect(JPEG)).contains(FileType.JPEG);
        assertThat(detect(PNG)).contains(FileType.PNG);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(detect("MZ\u0090\u0000\u0003\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(detect("PK\u0003\u0004zipfile".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(detect("GIF89a..".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(detect(new byte[0])).isEmpty();
    }

    @Test
    void truncatedHeadersAreRejected() {
        assertThat(detect(new byte[] {'%', 'P', 'D'})).isEmpty();
        assertThat(detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
    }

    @Test
    void declaredContentTypeMustAgreeWithTheContent() {
        assertThat(FileTypeDetector.matchesDeclared(FileType.PDF, "application/pdf")).isTrue();
        assertThat(FileTypeDetector.matchesDeclared(FileType.PDF, "Application/PDF; charset=binary")).isTrue();
        assertThat(FileTypeDetector.matchesDeclared(FileType.JPEG, "image/jpg")).isTrue();
        assertThat(FileTypeDetector.matchesDeclared(FileType.PNG, null)).isTrue();
        assertThat(FileTypeDetector.matchesDeclared(FileType.PNG, "application/pdf")).isFalse();
        assertThat(FileTypeDetector.matchesDeclared(FileType.PDF, "text/html")).isFalse();
    }
}
