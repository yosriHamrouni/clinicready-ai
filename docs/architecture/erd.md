# Entity relationships

Every tenant table carries `clinic_id`. Cross-table references are composite `(id, clinic_id)` foreign keys, so the database itself refuses a row that points into another clinic. Flyway owns the schema (`backend/src/main/resources/db/migration`).

```mermaid
erDiagram
    clinic ||--o{ app_user : employs
    clinic ||--o{ practitioner : has
    clinic ||--o{ patient : has
    clinic ||--o{ appointment_type : defines

    app_user }o--o| practitioner : "may be"
    practitioner ||--o{ appointment : sees
    patient ||--o{ appointment : books
    appointment_type ||--o{ appointment : "workflow of"
    appointment_type ||--o{ requirement_rule : requires

    patient ||--o{ document : owns
    document ||--o| document_text : "extracted text"
    document ||--o| document_extraction : "AI observation"
    document ||--o| document_metadata : "staff corrections"

    appointment ||--o| readiness_result : "latest checks"
    appointment ||--o{ follow_up_task : "follow-ups"
    follow_up_task ||--o{ follow_up_approval : decisions

    app_user ||--o{ agent_run : starts
    agent_run ||--o{ agent_action : "tool calls"
    agent_run |o--o{ follow_up_task : proposed

    clinic ||--o{ audit_event : records

    appointment {
        uuid id
        timestamptz starts_at
        varchar status
        jsonb expected_examinations
        numeric advance_amount
        uuid payment_confirmed_by
        timestamptz payment_confirmed_at
    }
    requirement_rule {
        uuid id
        varchar document_category
        boolean mandatory
        int validity_days
        boolean needs_verification
    }
    document {
        uuid id
        varchar category
        int version
        varchar status
        varchar extraction_status
        varchar sha256
    }
    readiness_result {
        uuid appointment_id
        varchar status
        jsonb checks
        timestamptz evaluated_at
    }
    follow_up_task {
        uuid id
        varchar requirement_code
        varchar status
        varchar check_status
        jsonb evidence_document_ids
        uuid run_id
        uuid approval_id
    }
    follow_up_approval {
        uuid id
        varchar decision
        uuid actor_id
        varchar comment
        timestamptz decided_at
    }
    agent_run {
        uuid id
        varchar request_text
        varchar status
        jsonb report
        int tool_calls
        int prompt_tokens
    }
    agent_action {
        uuid id
        int seq
        varchar tool
        jsonb arguments
        varchar result_status
        bigint latency_ms
    }
    audit_event {
        uuid id
        uuid actor_user_id
        varchar action
        varchar entity_type
        uuid entity_id
        jsonb change_summary
        uuid run_id
    }
```

## Notes

| Table | Why it looks like this |
| --- | --- |
| `document` | One row per stored file version; a new upload of the same category is version n+1. Readiness reads the newest version per category only. |
| `document_extraction` | What a model observed. Never updated; staff corrections go to `document_metadata` and win. |
| `readiness_result` | One row per appointment, replaced on every recalculation. `checks` holds each requirement's status, reason, remaining action, document references and staff confirmations. |
| `follow_up_task` | A partial unique index allows one open task (`PROPOSED`, `AWAITING_APPROVAL`, `APPROVED`, `DELIVERED`) per appointment and requirement. Checks forbid any status past `AWAITING_APPROVAL` without `approval_id`. |
| `follow_up_approval` | Immutable human decision. A rejection must carry a comment. |
| `agent_action` | Arguments stored redacted: only known argument names (ids, dates, codes) keep their values. |
| `audit_event` | Append-only. `actor_user_id` is null for system changes. |
