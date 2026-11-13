-- AI-extracted observations about a document (YOS-21): category, document date and issuer, with the
-- model's confidence and name.
--
-- These are observations, not decisions: nothing here says a requirement is met, a document is
-- authentic, or anything is covered or paid. Staff-corrected values live in document_metadata and
-- never overwrite these. One row per document; the document_id is the source-document reference.
-- A NULL category, date or issuer means "could not be identified", never a guess.

CREATE TABLE document_extraction (
    document_id    UUID PRIMARY KEY,
    clinic_id      UUID        NOT NULL REFERENCES clinic (id),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    -- Why the model output could not be used; set exactly when status is FAILED.
    failure        VARCHAR(20) CHECK (failure IN ('MODEL_ERROR', 'INVALID_OUTPUT')),
    category       VARCHAR(30)
        CHECK (category IN ('ID_DOCUMENT', 'INSURANCE_CARD', 'PRESCRIPTION', 'REFERRAL_LETTER',
                            'CONSENT_FORM', 'LAB_RESULT', 'IMAGING_REPORT', 'OTHER')),
    document_date  DATE,
    issuer         VARCHAR(200),
    confidence     DOUBLE PRECISION CHECK (confidence BETWEEN 0 AND 1),
    model_name     VARCHAR(100) NOT NULL,
    -- Set when a person must look at this document; NULL when no review is needed.
    review_reason  VARCHAR(20)
        CHECK (review_reason IN ('EXTRACTION_FAILED', 'LOW_CONFIDENCE', 'CATEGORY_UNKNOWN')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document_extraction_document_fk
        FOREIGN KEY (document_id, clinic_id) REFERENCES document (id, clinic_id),
    CONSTRAINT document_extraction_failure_ck
        CHECK ((status = 'FAILED') = (failure IS NOT NULL)),
    -- A failed extraction carries no values and always needs review; a successful one has a confidence.
    CONSTRAINT document_extraction_values_ck CHECK (
        (status = 'FAILED' AND category IS NULL AND document_date IS NULL AND issuer IS NULL
            AND confidence IS NULL AND review_reason = 'EXTRACTION_FAILED')
        OR (status = 'SUCCEEDED' AND confidence IS NOT NULL AND review_reason IS DISTINCT FROM 'EXTRACTION_FAILED'))
);
CREATE INDEX document_extraction_clinic_id_idx ON document_extraction (clinic_id);
CREATE INDEX document_extraction_review_idx ON document_extraction (clinic_id) WHERE review_reason IS NOT NULL;
