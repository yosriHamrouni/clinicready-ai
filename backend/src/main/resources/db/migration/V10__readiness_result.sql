-- Latest readiness result of each appointment (YOS-24): the aggregate status and every per-requirement
-- check with its evidence (document references and human confirmations), as JSON.
--
-- Recalculated in the same transaction as any change to its inputs (documents, corrections,
-- requirement rules, administrative data, payment confirmation), and on demand. An administrative
-- check only: no medical conclusion, no coverage guarantee.

ALTER TABLE appointment ADD CONSTRAINT appointment_id_clinic_key UNIQUE (id, clinic_id);

CREATE TABLE readiness_result (
    appointment_id UUID PRIMARY KEY,
    clinic_id      UUID        NOT NULL REFERENCES clinic (id),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('INCOMPLETE', 'NEEDS_REVIEW', 'READY')),
    checks         JSONB       NOT NULL,
    evaluated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT readiness_result_appointment_fk
        FOREIGN KEY (appointment_id, clinic_id) REFERENCES appointment (id, clinic_id) ON DELETE CASCADE
);
CREATE INDEX readiness_result_clinic_id_idx ON readiness_result (clinic_id);
