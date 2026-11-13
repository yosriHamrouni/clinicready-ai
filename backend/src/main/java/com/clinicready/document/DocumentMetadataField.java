package com.clinicready.document;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

/**
 * The administrative fields staff can read from a document and correct. All are administrative, so
 * practitioners (who may edit administrative fields only) may edit every one of them.
 */
public enum DocumentMetadataField {
    DOCUMENT_DATE("documentDate", true),
    EXPIRY_DATE("expiryDate", true),
    ISSUER("issuer", false),
    DOCUMENT_NUMBER("documentNumber", false),
    HOLDER_NAME("holderName", false);

    public static final int MAX_LENGTH = 200;

    private final String key;
    private final boolean date;

    DocumentMetadataField(String key, boolean date) {
        this.key = key;
        this.date = date;
    }

    /** The name used in the API. */
    public String key() {
        return key;
    }

    public static Optional<DocumentMetadataField> fromKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }

    /** Whether the (already trimmed, non-empty) value is acceptable for this field. */
    boolean accepts(String value) {
        if (value.length() > MAX_LENGTH) {
            return false;
        }
        if (!date) {
            return true;
        }
        try {
            return LocalDate.parse(value).toString().equals(value);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
