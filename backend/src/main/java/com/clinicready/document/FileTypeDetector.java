package com.clinicready.document;

import java.util.Locale;
import java.util.Optional;

/**
 * Identifies PDF, JPEG and PNG from the first bytes of the file. The client-supplied Content-Type
 * and file name are never trusted: an executable renamed to .pdf is rejected here.
 */
final class FileTypeDetector {

    /** Bytes needed to recognise every supported type. */
    static final int HEADER_BYTES = 8;

    enum FileType {
        PDF("application/pdf"),
        JPEG("image/jpeg"),
        PNG("image/png");

        private final String mimeType;

        FileType(String mimeType) {
            this.mimeType = mimeType;
        }

        String mimeType() {
            return mimeType;
        }
    }

    private FileTypeDetector() {
    }

    static Optional<FileType> detect(byte[] header, int length) {
        if (startsWith(header, length, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(FileType.PDF);
        }
        if (startsWith(header, length, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(FileType.JPEG);
        }
        if (startsWith(header, length, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(FileType.PNG);
        }
        return Optional.empty();
    }

    /** True if the client-declared Content-Type is consistent with the detected type. */
    static boolean matchesDeclared(FileType detected, String declaredContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return true;
        }
        String declared = declaredContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (declared.equals("image/jpg") || declared.equals("image/pjpeg")) {
            declared = "image/jpeg";
        }
        return declared.equals(detected.mimeType());
    }

    private static boolean startsWith(byte[] data, int length, int... expected) {
        if (length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
