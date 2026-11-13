package com.clinicready.document;

/**
 * Why a document was flagged for human review. A flagged document is never classified as missing:
 * the file exists, the system just is not sure enough about it.
 */
public enum ReviewReason {
    EXTRACTION_FAILED,
    LOW_CONFIDENCE,
    CATEGORY_UNKNOWN
}
