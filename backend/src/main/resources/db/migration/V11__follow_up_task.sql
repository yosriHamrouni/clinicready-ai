-- Follow-up tasks (YOS-28): what staff should do about one requirement of one appointment, proposed by the
-- agent's createFollowUpDraft tool or by staff. Created AWAITING_APPROVAL; nothing is sent from here. The
-- state machine and approvals are YOS-32; the unique open-task constraint is YOS-35.
--
-- Holds ids, categories and the readiness reason only: no patient identity details, no document text.

CREATE TABLE follow_up_task (
    id                    UUID PRIMARY KEY,
    clinic_id             UUID        NOT NULL REFERENCES clinic (id),
    appointment_id        UUID        NOT NULL,
    patient_id            UUID        NOT NULL,
    -- Document category of the rule, or EXPECTED_EXAMINATIONS / ADVANCE_PAYMENT.
    requirement_code      VARCHAR(40) NOT NULL,
    status                VARCHAR(20) NOT NULL
        CHECK (status IN ('PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'REJECTED', 'DELIVERED', 'RESOLVED')),
    -- The readiness check the task is about, as it was when the task was created.
    check_status          VARCHAR(20) NOT NULL
        CHECK (check_status IN ('MISSING', 'NEEDS_REVIEW', 'EXPIRED')),
    reason                VARCHAR(500) NOT NULL,
    suggested_action      VARCHAR(500),
    evidence_document_ids JSONB       NOT NULL DEFAULT '[]',
    language              VARCHAR(2)  NOT NULL CHECK (language IN ('FR', 'AR')),
    due_at                TIMESTAMPTZ NOT NULL,
    owner_user_id         UUID REFERENCES app_user (id),
    created_by            UUID        NOT NULL REFERENCES app_user (id),
    -- Set when an agent run created the task.
    run_id                UUID,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT follow_up_task_id_clinic_key UNIQUE (id, clinic_id),
    CONSTRAINT follow_up_task_appointment_fk
        FOREIGN KEY (appointment_id, clinic_id) REFERENCES appointment (id, clinic_id),
    CONSTRAINT follow_up_task_patient_fk
        FOREIGN KEY (patient_id, clinic_id) REFERENCES patient (id, clinic_id)
);
CREATE INDEX follow_up_task_clinic_status_idx ON follow_up_task (clinic_id, status);
CREATE INDEX follow_up_task_appointment_idx ON follow_up_task (appointment_id);
