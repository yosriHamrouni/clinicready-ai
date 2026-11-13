-- Administrative checklist data of an appointment (YOS-18).
--
-- The appointment is the admission record: its appointment type is the workflow type that decides
-- which requirements apply. Every field below is entered by staff. NULL means "not provided yet",
-- which stays distinct from a provided value and from a value a staff member has confirmed.

ALTER TABLE appointment
    ADD COLUMN trusted_contact_name         VARCHAR(200),
    ADD COLUMN trusted_contact_method       VARCHAR(10)
        CHECK (trusted_contact_method IN ('PHONE', 'EMAIL')),
    ADD COLUMN trusted_contact_value        VARCHAR(254),
    -- Set only when a staff member has actually reached the contact. A well-formed number is not enough.
    ADD COLUMN trusted_contact_confirmed_by UUID REFERENCES app_user (id),
    ADD COLUMN trusted_contact_confirmed_at TIMESTAMPTZ,
    -- JSON array of strings entered by staff. NULL = not entered; [] = staff stated none are expected.
    ADD COLUMN expected_examinations        JSONB
        CHECK (expected_examinations IS NULL OR jsonb_typeof(expected_examinations) = 'array'),
    -- Tunisian dinars (3 decimals), typed by staff. Never computed from the patient's condition.
    ADD COLUMN advance_amount               NUMERIC(12, 3) CHECK (advance_amount >= 0),
    -- Set only by an explicit staff confirmation; uploading payment evidence does not set it.
    ADD COLUMN payment_confirmed_by         UUID REFERENCES app_user (id),
    ADD COLUMN payment_confirmed_at         TIMESTAMPTZ,
    ADD COLUMN updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT appointment_trusted_contact_complete CHECK (
        (trusted_contact_name IS NULL AND trusted_contact_method IS NULL AND trusted_contact_value IS NULL)
        OR (trusted_contact_name IS NOT NULL AND trusted_contact_method IS NOT NULL
            AND trusted_contact_value IS NOT NULL)),
    ADD CONSTRAINT appointment_trusted_contact_confirmation CHECK (
        (trusted_contact_confirmed_by IS NULL AND trusted_contact_confirmed_at IS NULL)
        OR (trusted_contact_confirmed_by IS NOT NULL AND trusted_contact_confirmed_at IS NOT NULL
            AND trusted_contact_name IS NOT NULL)),
    ADD CONSTRAINT appointment_payment_confirmation CHECK (
        (payment_confirmed_by IS NULL AND payment_confirmed_at IS NULL)
        OR (payment_confirmed_by IS NOT NULL AND payment_confirmed_at IS NOT NULL
            AND advance_amount IS NOT NULL));

-- Appointment list filtered by status within a date range.
CREATE INDEX appointment_clinic_status_starts_at_idx ON appointment (clinic_id, status, starts_at);
