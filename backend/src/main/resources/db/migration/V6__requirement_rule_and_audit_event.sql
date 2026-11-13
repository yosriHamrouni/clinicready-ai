-- Requirement rules (YOS-16) and the audit trail they are the first writer of (read side: YOS-36).
--
-- Same tenant rules as V3: NOT NULL clinic_id, an index led by clinic_id, and composite foreign keys
-- on (id, clinic_id), so a rule can never hang off another clinic's appointment type.

-- A document an appointment type needs. At most one rule per (appointment type, category).
CREATE TABLE requirement_rule (
    id                  UUID PRIMARY KEY,
    clinic_id           UUID        NOT NULL REFERENCES clinic (id),
    appointment_type_id UUID        NOT NULL,
    document_category   VARCHAR(30) NOT NULL
        CHECK (document_category IN ('ID_DOCUMENT', 'INSURANCE_CARD', 'PRESCRIPTION', 'REFERRAL_LETTER',
                                     'CONSENT_FORM', 'LAB_RESULT', 'IMAGING_REPORT', 'OTHER')),
    -- false: optional, reported but never blocks readiness.
    mandatory           BOOLEAN     NOT NULL,
    -- Days a document stays valid after upload; NULL = never expires.
    validity_days       INTEGER CHECK (validity_days BETWEEN 1 AND 3650),
    -- A matching document counts only once a staff member has verified it.
    needs_verification  BOOLEAN     NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT requirement_rule_id_clinic_key UNIQUE (id, clinic_id),
    CONSTRAINT requirement_rule_appointment_type_fk
        FOREIGN KEY (appointment_type_id, clinic_id) REFERENCES appointment_type (id, clinic_id),
    -- Also serves as the clinic_id index.
    CONSTRAINT requirement_rule_type_category_key UNIQUE (clinic_id, appointment_type_id, document_category)
);

-- Who changed what, and when. Append-only; never holds document text or patient identity details.
CREATE TABLE audit_event (
    id             UUID PRIMARY KEY,
    clinic_id      UUID        NOT NULL REFERENCES clinic (id),
    -- NULL for changes made by the system itself.
    actor_user_id  UUID REFERENCES app_user (id),
    action         VARCHAR(50) NOT NULL,
    entity_type    VARCHAR(50) NOT NULL,
    entity_id      UUID        NOT NULL,
    change_summary JSONB       NOT NULL,
    -- Set when the change happened during an agent run.
    run_id         UUID,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_event_clinic_occurred_at_idx ON audit_event (clinic_id, occurred_at DESC);
CREATE INDEX audit_event_entity_idx ON audit_event (entity_type, entity_id);
