package com.clinicready.document;

/** Review state of an uploaded document. New uploads start as {@link #UPLOADED}. */
public enum DocumentStatus {
    UPLOADED,
    NEEDS_REVIEW,
    VERIFIED,
    REJECTED
}
