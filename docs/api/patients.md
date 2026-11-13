# Patients API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

Synthetic data only. Logs and audit events carry patient ids and references, never names or phone numbers.

## Permissions

| Action | RECEPTIONIST | PRACTITIONER | CLINIC_MANAGER |
| --- | --- | --- | --- |
| List, search and read patients | all in clinic | patients they have an appointment with | all in clinic |
| Create a patient | yes | no (403) | yes |

A patient of another clinic, or one a practitioner may not see, is reported as `404 NOT_FOUND`.

## Patient object

```json
{
  "id": "5b1c…",
  "patientRef": "P-0042",
  "fullName": "Amel Test",
  "phone": "+216 20 000 001",
  "preferredLanguage": "AR"
}
```

- `patientRef`: staff-facing reference, unique within the clinic. Used in prompts instead of identity details.
- `phone`: optional. Only its format is checked: 8 to 15 digits, with an optional leading `+`, spaces, dots, dashes or parentheses. A well-formed number says nothing about whether it is reachable.
- `preferredLanguage`: `FR` or `AR`.

## `GET /patients?q=`

`200 OK`: visible patients ordered by reference, at most 200. `q` (optional) keeps patients whose name or reference contains it, case-insensitively; `%` and `_` are matched literally.

## `GET /patients/{id}`

`200 OK` with the patient object.

## `POST /patients`

```json
{ "fullName": "Amel Test", "phone": "+216 20 000 001", "preferredLanguage": "AR", "patientRef": "P-0042" }
```

Only `fullName` is required. Without `patientRef` the next free `P-NNNN` reference is generated. `preferredLanguage` defaults to `FR`.

`201 Created` with a `Location: /api/v1/patients/{id}` header and the patient object. Writes a `PATIENT_CREATED` audit event (patient id and reference only).

## Errors

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Blank or too long `fullName`; malformed `phone`; `patientRef` not letters, digits and dashes (max 20); unknown language. |
| 401 | `UNAUTHENTICATED` | No or invalid token. |
| 403 | `FORBIDDEN` | Create by a practitioner. |
| 404 | `NOT_FOUND` | Patient does not exist or is not visible. |
| 409 | `PATIENT_REF_CONFLICT` | The reference is already used in the clinic. |
