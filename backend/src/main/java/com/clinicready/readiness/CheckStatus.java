package com.clinicready.readiness;

/** Outcome of checking one requirement for one appointment. Meanings match docs/prd.md. */
public enum CheckStatus {
    /** A matching document (or fact) has been identified. Presence alone; see the reason for detail. */
    PRESENT,
    /** An applicable, expected item is absent from the record (including: every match was rejected). */
    MISSING,
    /** Uncertainty, inconsistency, or pending human verification. Never used for absence. */
    NEEDS_REVIEW,
    /** A configured validity rule is not satisfied. Blocks readiness when the requirement is mandatory. */
    EXPIRED,
    /** A configured exemption or an authorized human decision says the requirement does not apply. */
    NOT_APPLICABLE
}
