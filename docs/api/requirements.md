# Requirements API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

A requirement rule says which document an appointment type needs. The readiness engine checks every appointment against the rules of its type. A type has at most one rule per document category.

## Permissions

| Action | RECEPTIONIST | PRACTITIONER | CLINIC_MANAGER |
| --- | --- | --- | --- |
| List appointment types and their rules | yes | yes | yes |
| Create, update or delete a rule | no (403) | no (403) | yes |

Everyone can read rules because readiness checks run for every role. An appointment type or rule of another clinic is reported as `404 NOT_FOUND`.

Every create, update and delete writes an audit event (`REQUIREMENT_RULE_CREATED`, `REQUIREMENT_RULE_UPDATED`, `REQUIREMENT_RULE_DELETED`) in the same transaction, with the actor, the rule id, the appointment type id and the field values before and after. An update that changes nothing writes no event. The audit log endpoint comes with YOS-36.

## Rule object

```json
{
  "id": "3c7e…",
  "appointmentTypeId": "a41f…",
  "documentCategory": "CONSENT_FORM",
  "mandatory": true,
  "validityDays": 30,
  "needsVerification": true,
  "updatedAt": "2026-11-13T09:12:44.120Z"
}
```

- `documentCategory`: one of the document categories in [documents.md](documents.md).
- `mandatory`: `false` makes the document optional: it is reported, but never blocks readiness.
- `validityDays`: how many days a document stays valid after upload, 1 to 3650. `null` means it never expires.
- `needsVerification`: a matching document counts only after a staff member has verified it.

## `GET /appointment-types`

`200 OK`: the clinic's appointment types, by code.

```json
[{ "id": "a41f…", "code": "MRI", "label": "IRM" }]
```

## `GET /appointment-types/{id}/requirements`

`200 OK`: array of rule objects for the type, ordered by category. `404` if the type is not in the user's clinic.

## `POST /appointment-types/{id}/requirements`

Manager only. Body: every rule field except `id`, `appointmentTypeId` and `updatedAt`:

```json
{ "documentCategory": "CONSENT_FORM", "mandatory": true, "validityDays": 30, "needsVerification": true }
```

`201 Created` with a `Location: /api/v1/requirements/{id}` header and the rule object.

## `PUT /requirements/{id}`

Manager only. Same body as `POST`; it replaces every field. `200 OK` with the rule object.

## `DELETE /requirements/{id}`

Manager only. `204 No Content`.

## Errors

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing `documentCategory`, `mandatory` or `needsVerification`; unknown category; `validityDays` outside 1–3650. |
| 401 | `UNAUTHENTICATED` | No or invalid token. |
| 403 | `FORBIDDEN` | Write by a receptionist or practitioner. |
| 404 | `NOT_FOUND` | Appointment type or rule does not exist or belongs to another clinic. |
| 409 | `RULE_CONFLICT` | The type already has a rule for this category. |
