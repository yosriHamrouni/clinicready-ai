-- Core domain (YOS-11): practitioners, patients, appointment types and appointments.
--
-- Every tenant-owned table has a NOT NULL clinic_id and an index whose first column is clinic_id.
-- References between tenant-owned rows use composite foreign keys on (id, clinic_id), so a row can
-- never point at a row of another clinic, even through a bug in the application.

ALTER TABLE clinic ADD COLUMN city VARCHAR(100);

CREATE TABLE practitioner (
    id         UUID PRIMARY KEY,
    clinic_id  UUID         NOT NULL REFERENCES clinic (id),
    full_name  VARCHAR(200) NOT NULL,
    specialty  VARCHAR(100),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT practitioner_id_clinic_key UNIQUE (id, clinic_id)
);
CREATE INDEX practitioner_clinic_id_idx ON practitioner (clinic_id);

-- A user may be a practitioner (role PRACTITIONER sees only their own appointments).
ALTER TABLE app_user ADD COLUMN practitioner_id UUID;
ALTER TABLE app_user ADD CONSTRAINT app_user_practitioner_fk
    FOREIGN KEY (practitioner_id, clinic_id) REFERENCES practitioner (id, clinic_id);
CREATE UNIQUE INDEX app_user_practitioner_id_key ON app_user (practitioner_id)
    WHERE practitioner_id IS NOT NULL;

CREATE TABLE patient (
    id                 UUID PRIMARY KEY,
    clinic_id          UUID         NOT NULL REFERENCES clinic (id),
    -- Human-facing reference shown to staff and used in prompts instead of identity details.
    patient_ref        VARCHAR(20)  NOT NULL,
    full_name          VARCHAR(200) NOT NULL,
    phone              VARCHAR(30),
    preferred_language VARCHAR(2)   NOT NULL DEFAULT 'FR'
        CHECK (preferred_language IN ('FR', 'AR')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT patient_id_clinic_key UNIQUE (id, clinic_id),
    -- Also serves as the clinic_id index.
    CONSTRAINT patient_clinic_ref_key UNIQUE (clinic_id, patient_ref)
);

CREATE TABLE appointment_type (
    id         UUID PRIMARY KEY,
    clinic_id  UUID         NOT NULL REFERENCES clinic (id),
    code       VARCHAR(50)  NOT NULL,
    label      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT appointment_type_id_clinic_key UNIQUE (id, clinic_id),
    -- Also serves as the clinic_id index.
    CONSTRAINT appointment_type_clinic_code_key UNIQUE (clinic_id, code)
);

CREATE TABLE appointment (
    id                  UUID PRIMARY KEY,
    clinic_id           UUID        NOT NULL REFERENCES clinic (id),
    patient_id          UUID        NOT NULL,
    practitioner_id     UUID        NOT NULL,
    appointment_type_id UUID        NOT NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT appointment_patient_fk
        FOREIGN KEY (patient_id, clinic_id) REFERENCES patient (id, clinic_id),
    CONSTRAINT appointment_practitioner_fk
        FOREIGN KEY (practitioner_id, clinic_id) REFERENCES practitioner (id, clinic_id),
    CONSTRAINT appointment_type_fk
        FOREIGN KEY (appointment_type_id, clinic_id) REFERENCES appointment_type (id, clinic_id)
);
-- "Tomorrow's appointments" for the clinic, and for one practitioner.
CREATE INDEX appointment_clinic_starts_at_idx ON appointment (clinic_id, starts_at);
CREATE INDEX appointment_clinic_practitioner_starts_at_idx
    ON appointment (clinic_id, practitioner_id, starts_at);
CREATE INDEX appointment_patient_id_idx ON appointment (patient_id);
