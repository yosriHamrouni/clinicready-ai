package com.clinicready.document;

/**
 * Whether the system could read a document's text. Independent of the review {@link DocumentStatus}.
 *
 * <p>{@link #EXTRACTION_FAILED} never means the document is missing: the file exists, the system just
 * cannot read it, so readiness must report the requirement as {@code NEEDS_REVIEW}.
 */
public enum ExtractionStatus {
    PENDING,
    EXTRACTED,
    EXTRACTION_FAILED
}
