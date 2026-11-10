-- Minimal clinic and user tables for authentication (YOS-10).
-- YOS-11 extends them with the rest of the core model.

CREATE TABLE clinic (
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    clinic_id     UUID         NOT NULL REFERENCES clinic (id),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role          VARCHAR(32)  NOT NULL
        CHECK (role IN ('RECEPTIONIST', 'PRACTITIONER', 'CLINIC_MANAGER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are unique across the platform and compared case-insensitively.
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email));
CREATE INDEX app_user_clinic_id_idx ON app_user (clinic_id);
