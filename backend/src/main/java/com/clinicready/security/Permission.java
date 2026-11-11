package com.clinicready.security;

/**
 * Actions checked by {@link AuthorizationService}, one per row of the PRD permission matrix (see
 * docs/prd.md). A permission marked "scoped" is held by a role that may use it on part of the
 * clinic's data only; the owning service applies that narrower scope.
 */
public enum Permission {
    /** Scoped for practitioners: patients they have an appointment with. */
    PATIENT_READ,
    PATIENT_WRITE,
    /** Scoped for practitioners: their own appointments. */
    APPOINTMENT_READ,
    APPOINTMENT_WRITE,
    /** Scoped for practitioners: documents of their own patients. */
    DOCUMENT_READ,
    DOCUMENT_UPLOAD,
    /** Scoped for practitioners: administrative fields only. */
    DOCUMENT_METADATA_EDIT,
    /** Scoped for practitioners: their own appointments. */
    AGENT_RUN,
    FOLLOWUP_APPROVE,
    REQUIREMENT_RULES_MANAGE,
    USER_MANAGE,
    /** Reminder templates and approval policy. */
    TEMPLATE_MANAGE,
    /** Scoped for receptionists: their own actions. */
    AUDIT_READ
}
