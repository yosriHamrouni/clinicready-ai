-- Staff review of a document (YOS-46): who verified or rejected it, when, and why it was rejected.
-- The status values already exist (V4); until now nothing in the app set VERIFIED or REJECTED.
ALTER TABLE document
    ADD COLUMN review_note VARCHAR(500),
    ADD COLUMN reviewed_by UUID REFERENCES app_user (id),
    ADD COLUMN reviewed_at TIMESTAMPTZ;
