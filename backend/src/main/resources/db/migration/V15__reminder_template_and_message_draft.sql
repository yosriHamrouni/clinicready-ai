-- Reminder templates and message drafts (YOS-31).
--
-- A manager writes one template per requirement code and language. requirement_code 'DEFAULT' is the
-- clinic's fallback for any requirement; without one, built-in texts are used. Templates hold only fixed
-- text and {{slots}} (patientName, requirement, appointmentDate, clinicName) that code fills: no model
-- writes into a draft, and no slot carries medical content.

CREATE TABLE reminder_template (
    id               UUID PRIMARY KEY,
    clinic_id        UUID          NOT NULL REFERENCES clinic (id),
    requirement_code VARCHAR(40)   NOT NULL,
    language         VARCHAR(2)    NOT NULL CHECK (language IN ('FR', 'AR')),
    body             VARCHAR(1000) NOT NULL,
    updated_by       UUID          NOT NULL REFERENCES app_user (id),
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT reminder_template_key UNIQUE (clinic_id, requirement_code, language)
);

-- The message staff will send for one follow-up task, built from a template when the task is created and
-- editable by staff until the task is decided.
CREATE TABLE message_draft (
    id          UUID PRIMARY KEY,
    clinic_id   UUID          NOT NULL REFERENCES clinic (id),
    task_id     UUID          NOT NULL UNIQUE,
    language    VARCHAR(2)    NOT NULL CHECK (language IN ('FR', 'AR')),
    body        VARCHAR(2000) NOT NULL,
    -- NULL when a built-in text was used.
    template_id UUID REFERENCES reminder_template (id) ON DELETE SET NULL,
    -- NULL until a staff member edits the generated text.
    edited_by   UUID REFERENCES app_user (id),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT message_draft_task_fk FOREIGN KEY (task_id, clinic_id) REFERENCES follow_up_task (id, clinic_id)
);
