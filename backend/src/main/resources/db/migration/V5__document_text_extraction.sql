-- Text extraction for uploaded documents (YOS-19).
--
-- extraction_status is separate from the review status: it says whether the system could read the
-- file, not whether the document is acceptable. A failed extraction is NOT a missing document; the
-- readiness engine treats it as NEEDS_REVIEW.

ALTER TABLE document
    ADD COLUMN extraction_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (extraction_status IN ('PENDING', 'EXTRACTED', 'EXTRACTION_FAILED')),
    ADD COLUMN extraction_failure VARCHAR(30)
        CHECK (extraction_failure IN ('NO_TEXT_LAYER', 'ENCRYPTED', 'UNREADABLE', 'FILE_MISSING',
                                      'INTERNAL_ERROR')),
    ADD COLUMN extracted_at       TIMESTAMPTZ,
    -- A reason exactly when the extraction failed; a timestamp exactly when it finished.
    ADD CONSTRAINT document_extraction_failure_ck
        CHECK ((extraction_status = 'EXTRACTION_FAILED') = (extraction_failure IS NOT NULL)),
    ADD CONSTRAINT document_extracted_at_ck
        CHECK ((extraction_status = 'PENDING') = (extracted_at IS NULL));

-- The recovery sweep looks for documents still waiting.
CREATE INDEX document_extraction_pending_idx ON document (uploaded_at) WHERE extraction_status = 'PENDING';

-- Extracted text, one row per document that was EXTRACTED. Kept out of the document table so
-- metadata queries never load it. Never logged.
CREATE TABLE document_text (
    document_id UUID PRIMARY KEY,
    clinic_id   UUID        NOT NULL REFERENCES clinic (id),
    content     TEXT        NOT NULL,
    page_count  INTEGER     NOT NULL CHECK (page_count >= 0),
    -- True when the text was cut at the page or character limit.
    truncated   BOOLEAN     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document_text_document_fk
        FOREIGN KEY (document_id, clinic_id) REFERENCES document (id, clinic_id)
);
CREATE INDEX document_text_clinic_id_idx ON document_text (clinic_id);
