# Audit events

Read-only view of the append-only audit trail. Every state change, and every agent run, is recorded by
`AuditService` in the same transaction as the change.

## `GET /api/v1/audit-events`

Newest first.

| Query parameter | Meaning |
| --- | --- |
| `runId` | Only events recorded during this agent run |
| `entityType` | e.g. `PATIENT`, `APPOINTMENT`, `FOLLOW_UP_TASK`, `AGENT_RUN` |
| `entityId` | Events about one entity |
| `from`, `to` | ISO-8601 instants, inclusive. `from` after `to` is a 400 |
| `page` (default 0), `size` (default 50, max 100) | Paging |

Response:

```json
{
  "items": [
    {
      "id": "…",
      "action": "AGENT_TOOL_CALLED",
      "entityType": "AGENT_RUN",
      "entityId": "<run id>",
      "actorUserId": "…",
      "actorName": "Sarra Test",
      "runId": "<run id>",
      "changeSummary": { "tool": "checkReadiness", "seq": 2, "resultStatus": "OK" },
      "occurredAt": "2026-11-20T09:00:03Z"
    }
  ],
  "page": 0,
  "size": 50,
  "total": 1
}
```

`actorName` is null for changes made by the system. `changeSummary` holds ids and field values only, never
document text or patient identity details.

### Who sees what

| Role | Result |
| --- | --- |
| Clinic manager | Every event of the clinic |
| Receptionist | Only events of their own actions |
| Practitioner | 403 |

Events of other clinics are never returned.

## Agent run events

| Action | When | `changeSummary` |
| --- | --- | --- |
| `AGENT_RUN_STARTED` | The worker starts the run | `{}` |
| `AGENT_TOOL_CALLED` | Each tool call attempt, in order | `tool`, `seq`, `resultStatus` (arguments stay in `agent_action`, redacted) |
| `AGENT_RUN_FINISHED` | The run ends, whatever the status | `status`, `toolCalls` |

The actor is the user who started the run. These events commit in their own transaction, and a failure to
write one is logged and never ends the run.

Every other audit event recorded while a run is working (for example `FOLLOW_UP_TASK_CREATED` for the
agent's proposal) has the run's id in the `run_id` column, so `GET /audit-events?runId=…` returns the whole
trail of a run. Actions a person takes later (approving or delivering the follow-up) have no run id.
