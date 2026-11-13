-- Fields read from a document (dates, issuer, number...) and corrected by staff (YOS-20).
--
-- One row per document, created the first time fields are saved. Kept out of the document table so
-- file metadata stays immutable. Same tenant rules as V3/V4: NOT NULL clinic_id and a composite
-- foreign key on (document_id, clinic_id).

CREATE TABLE document_metadata (
    document_id UUID PRIMARY KEY,
    clinic_id   UUID        NOT NULL REFERENCES clinic (id),
    -- Current values by field name; a missing key means "not set".
    fields      JSONB       NOT NULL CHECK (jsonb_typeof(fields) = 'object'),
    -- Who last edited the fields, and when.
    updated_by  UUID        NOT NULL REFERENCES app_user (id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document_metadata_document_fk
        FOREIGN KEY (document_id, clinic_id) REFERENCES document (id, clinic_id)
);
CREATE INDEX document_metadata_clinic_id_idx ON document_metadata (clinic_id);
