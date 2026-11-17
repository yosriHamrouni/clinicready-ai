# Follow-ups and approvals API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

A follow-up task is what staff should do about one requirement of one appointment. The agent can propose one (`createFollowUpDraft`) and, once a person has approved it, execute it (`submitApprovedAction`). Only people decide: no agent tool reaches the approve or reject endpoints.

## Lifecycle

`PROPOSED → AWAITING_APPROVAL → APPROVED | REJECTED`, `APPROVED → DELIVERED → RESOLVED`. Anything else is `409 INVALID_TRANSITION`. Tasks are created `AWAITING_APPROVAL`. An open task (`PROPOSED`, `AWAITING_APPROVAL`, `APPROVED`, `DELIVERED`) blocks a second one for the same appointment and requirement.

- Delivery needs the task's recorded `APPROVED` decision and triggers a readiness re-check of the appointment.
- `submitApprovedAction(taskId, approvalId)` fails with `CONFLICT` unless `approvalId` is that task's `APPROVED` decision and the task is still `APPROVED` (an approval is used once).

## Permissions

| Action | RECEPTIONIST | PRACTITIONER | CLINIC_MANAGER |
| --- | --- | --- | --- |
| List and read tasks | all in clinic | tasks of own appointments | all in clinic |
| Approve, reject, mark delivered, resolve | yes | no (403) | yes |

A task of another clinic, or of another practitioner's appointment for a practitioner, is `404 NOT_FOUND`.

## Endpoints

| Endpoint | Body | Result |
| --- | --- | --- |
| `GET /follow-up-tasks?status=…&appointmentId=…` | | Tasks, earliest due first, at most 200. Default: open tasks. With `appointmentId`: every task of that appointment. |
| `GET /follow-up-tasks/{id}` | | The task with its decisions. |
| `POST /follow-up-tasks/{id}/approve` | `{ "comment": "…" }` optional | `APPROVED`; records actor, comment, time. |
| `POST /follow-up-tasks/{id}/reject` | `{ "comment": "…" }` required | `REJECTED`. |
| `POST /follow-up-tasks/{id}/delivered` | | `DELIVERED`: staff delivered the reminder themselves. |
| `POST /follow-up-tasks/{id}/resolve` | | `RESOLVED`. |

Every change writes an audit event: `FOLLOW_UP_TASK_CREATED`, `_APPROVED`, `_REJECTED`, `_DELIVERED`, `_RESOLVED`.

## Task object

```json
{
  "id": "…", "appointmentId": "…", "patientId": "…",
  "requirementCode": "CONSENT_FORM",
  "status": "APPROVED",
  "checkStatus": "MISSING",
  "reason": "No CONSENT_FORM document found in the sources checked: …",
  "suggestedAction": "Obtain and upload a CONSENT_FORM document",
  "evidenceDocumentIds": [],
  "language": "FR",
  "dueAt": "2026-11-18T08:30:00Z",
  "createdBy": "…", "runId": "…",
  "approvalId": "…", "deliveredAt": null, "deliveredBy": null, "deliveredByRun": null, "resolvedAt": null,
  "createdAt": "…", "updatedAt": "…",
  "approvals": [ { "id": "…", "decision": "APPROVED", "actorId": "…", "comment": "ok", "decidedAt": "…" } ]
}
```

The message draft (template, FR/AR body) is YOS-31 and not part of this object yet.
