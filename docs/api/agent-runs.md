# Agent runs API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

The agent sits on a read-and-draft path. It can read appointments, requirements, documents and readiness, and propose follow-up tasks (`AWAITING_APPROVAL`). It cannot approve, send or execute anything: approvals are a separate API with no agent tool.

## Permissions

`AGENT_RUN`: receptionists and managers for the whole clinic, practitioners for their own appointments. The run executes as its initiator, so every tool sees exactly what the initiator may see. A practitioner reads only the runs they started. Anything else is `404 NOT_FOUND`.

## `POST /agent-runs`

Body: `{ "request": "Prepare tomorrow's appointments for Dr. Ben Salem" }` (1 to 500 characters).

`202 Accepted`, `Location: /api/v1/agent-runs/{id}`, body `{ "runId": "…", "status": "QUEUED" }`. The run is stored before it starts, so a page refresh loses nothing.

## `GET /agent-runs/{id}`

```json
{
  "id": "…",
  "request": "Prepare tomorrow's appointments for Dr. Ben Salem",
  "status": "SUCCEEDED",
  "statusReason": null,
  "report": {
    "summary": "One appointment is incomplete.",
    "counts": { "ready": 0, "needsReview": 0, "incomplete": 1 },
    "appointments": [
      { "appointmentId": "…", "status": "INCOMPLETE",
        "findings": [ { "requirementCode": "CONSENT_FORM", "status": "MISSING", "action": "Ask for the consent form" } ] }
    ]
  },
  "toolCalls": 3,
  "promptTokens": 40,
  "completionTokens": 20,
  "createdAt": "…", "startedAt": "…", "finishedAt": "…",
  "actions": [
    { "seq": 1, "tool": "checkReadiness", "arguments": { "appointmentId": "…" }, "resultStatus": "OK",
      "latencyMs": 12, "tokens": 15, "at": "…" }
  ]
}
```

| Status | Meaning |
| --- | --- |
| `QUEUED`, `RUNNING` | Not finished; poll again. |
| `SUCCEEDED` | The report passed validation and the readiness cross-check. |
| `FAILED` | No model configured, an unexpected error, a restart, or a final answer that is not valid report JSON. `statusReason` says which. |
| `TOOL_LIMIT_EXCEEDED` | More than 15 tool calls (`clinicready.ai.max-tool-calls`). |
| `TIMED_OUT` | Longer than 90 s (`clinicready.ai.run-timeout`), checked before every model and tool call. |
| `REPORT_MISMATCH` | The report contradicts the stored readiness results: wrong aggregate, wrong or missing finding, or a checked appointment left out. |

- `counts` are computed by the server from `appointments`, never taken from the model.
- `arguments` keep only known argument names (ids, dates, codes, language, fields); any other value is `"[redacted]"`.
- `tokens` on an action are those of the model turn that asked for it, counted on the first call of that turn.
