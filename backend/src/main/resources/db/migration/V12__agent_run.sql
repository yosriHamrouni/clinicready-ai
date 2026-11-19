-- Agent runs and their tool calls (YOS-29). A run is persisted before it starts, so a refreshed page finds
-- it again. Arguments are stored redacted: ids, dates and codes only. No document text, no patient identity.

CREATE TABLE agent_run (
    id                 UUID PRIMARY KEY,
    clinic_id          UUID         NOT NULL REFERENCES clinic (id),
    initiator_id       UUID         NOT NULL REFERENCES app_user (id),
    request_text       VARCHAR(500) NOT NULL,
    status             VARCHAR(30)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED',
                                                               'TOOL_LIMIT_EXCEEDED', 'TIMED_OUT', 'REPORT_MISMATCH')),
    -- Why the run ended the way it did; NULL while queued or running, and on success.
    status_reason      VARCHAR(500),
    -- The validated final report: summary, counts and per-appointment findings.
    report             JSONB,
    tool_calls         INTEGER      NOT NULL DEFAULT 0,
    prompt_tokens      INTEGER      NOT NULL DEFAULT 0,
    completion_tokens  INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    CONSTRAINT agent_run_id_clinic_key UNIQUE (id, clinic_id)
);
CREATE INDEX agent_run_clinic_created_idx ON agent_run (clinic_id, created_at DESC);
CREATE INDEX agent_run_unfinished_idx ON agent_run (status) WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE agent_action (
    id             UUID PRIMARY KEY,
    clinic_id      UUID         NOT NULL,
    run_id         UUID         NOT NULL,
    seq            INTEGER      NOT NULL,
    tool           VARCHAR(60)  NOT NULL,
    arguments      JSONB        NOT NULL,
    -- The tool's ToolResult status (OK, NOT_FOUND, FORBIDDEN, INVALID_INPUT, ERROR), or UNKNOWN_TOOL.
    result_status  VARCHAR(20)  NOT NULL,
    latency_ms     BIGINT       NOT NULL,
    -- Tokens of the model turn that asked for this call; counted on the first call of that turn only.
    tokens         INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT agent_action_run_fk FOREIGN KEY (run_id, clinic_id) REFERENCES agent_run (id, clinic_id),
    CONSTRAINT agent_action_run_seq_key UNIQUE (run_id, seq)
);
