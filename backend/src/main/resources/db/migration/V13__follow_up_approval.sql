-- Follow-up approvals and delivery (YOS-32). An approval is a human decision recorded through the
-- approval API only; no agent tool can create one. A task is delivered (its action executed) only after an
-- APPROVED decision, and delivery triggers a readiness re-check.

CREATE TABLE follow_up_approval (
    id          UUID PRIMARY KEY,
    clinic_id   UUID         NOT NULL REFERENCES clinic (id),
    task_id     UUID         NOT NULL,
    decision    VARCHAR(10)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    actor_id    UUID         NOT NULL REFERENCES app_user (id),
    comment     VARCHAR(500),
    decided_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT follow_up_approval_task_fk FOREIGN KEY (task_id, clinic_id) REFERENCES follow_up_task (id, clinic_id),
    -- A rejection must say why.
    CONSTRAINT follow_up_approval_reject_comment_ck CHECK (decision = 'APPROVED' OR comment IS NOT NULL)
);
CREATE INDEX follow_up_approval_task_idx ON follow_up_approval (task_id);

ALTER TABLE follow_up_task
    ADD COLUMN approval_id      UUID REFERENCES follow_up_approval (id),
    ADD COLUMN delivered_at     TIMESTAMPTZ,
    ADD COLUMN delivered_by     UUID REFERENCES app_user (id),
    -- Set when an agent run executed the approved action through submitApprovedAction.
    ADD COLUMN delivered_by_run UUID,
    ADD COLUMN resolved_at      TIMESTAMPTZ,
    ADD CONSTRAINT follow_up_task_delivered_ck
        CHECK ((status IN ('DELIVERED', 'RESOLVED')) = (delivered_at IS NOT NULL)),
    -- Nothing past AWAITING_APPROVAL without a recorded decision.
    ADD CONSTRAINT follow_up_task_decided_ck
        CHECK (status IN ('PROPOSED', 'AWAITING_APPROVAL') OR approval_id IS NOT NULL);
