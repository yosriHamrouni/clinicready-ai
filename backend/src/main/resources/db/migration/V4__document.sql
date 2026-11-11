-- Patient documents (YOS-15). The file itself lives outside the database (see DocumentStorage);
-- this table holds its metadata, SHA-256 checksum and storage key.
--
-- Same tenant rules as V3: NOT NULL clinic_id and a composite foreign key on (patient_id, clinic_id),
-- so a document can never point at a patient of another clinic.

CREATE TABLE document (
    id                UUID PRIMARY KEY,
    clinic_id         UUID         NOT NULL REFERENCES clinic (id),
    patient_id        UUID         NOT NULL,
    category          VARCHAR(30)  NOT NULL
        CHECK (category IN ('ID_DOCUMENT', 'INSURANCE_CARD', 'PRESCRIPTION', 'REFERRAL_LETTER',
                            'CONSENT_FORM', 'LAB_RESULT', 'IMAGING_REPORT', 'OTHER')),
    source            VARCHAR(30)  NOT NULL
        CHECK (source IN ('STAFF_UPLOAD', 'EMAIL', 'SCAN', 'PATIENT_SUBMISSION')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'NEEDS_REVIEW', 'VERIFIED', 'REJECTED')),
    -- 1, 2, 3... per (patient, category): a new upload of the same category is a new version.
    version           INTEGER      NOT NULL CHECK (version >= 1),
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    -- Lowercase hex SHA-256 of the stored bytes.
    sha256            VARCHAR(64)  NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    -- Opaque key understood by DocumentStorage; never derived from user input.
    storage_key       VARCHAR(255) NOT NULL,
    uploaded_by       UUID         NOT NULL REFERENCES app_user (id),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT document_id_clinic_key UNIQUE (id, clinic_id),
    CONSTRAINT document_patient_fk
        FOREIGN KEY (patient_id, clinic_id) REFERENCES patient (id, clinic_id),
    -- Also guards against two concurrent uploads picking the same version.
    CONSTRAINT document_version_key UNIQUE (clinic_id, patient_id, category, version),
    CONSTRAINT document_storage_key_key UNIQUE (storage_key)
);
CREATE INDEX document_clinic_id_idx ON document (clinic_id);
CREATE INDEX document_patient_id_idx ON document (patient_id);
