package com.clinicready.document;

/** Why text extraction failed. Set only when the status is {@link ExtractionStatus#EXTRACTION_FAILED}. */
public enum ExtractionFailure {
    /** An image, or a PDF without a text layer (a scan). Needs OCR (YOS-22). */
    NO_TEXT_LAYER,
    /** A PDF protected by a password. */
    ENCRYPTED,
    /** The file is corrupt or not parseable. */
    UNREADABLE,
    /** Metadata exists but the stored file does not. */
    FILE_MISSING,
    /** Unexpected error in the extractor. */
    INTERNAL_ERROR
}
