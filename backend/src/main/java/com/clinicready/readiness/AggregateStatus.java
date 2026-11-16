package com.clinicready.readiness;

/**
 * Readiness of a whole appointment, from its mandatory checks only. {@link #READY} means administratively
 * ready: it is not a medical admission decision and does not guarantee financial coverage.
 */
public enum AggregateStatus {
    /** A mandatory requirement is missing, expired, or confirmed non-compliant. */
    INCOMPLETE,
    /** Nothing mandatory is missing, but a mandatory requirement needs human verification. */
    NEEDS_REVIEW,
    READY
}
