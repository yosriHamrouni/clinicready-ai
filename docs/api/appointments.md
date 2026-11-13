# Appointments API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

## Admission model

The appointment is the admission record. Its **appointment type is the workflow type**: it decides which requirement rules apply ([requirements.md](requirements.md)). The administrative checklist data of the supported workflow lives on the appointment:

| Item | Entered by | "Missing" | "Provided" | "Confirmed" |
| --- | --- | --- | --- | --- |
| Trusted contact (name, method, value) | staff | not entered | entered | a staff member reached the contact |
| Expected examinations | staff | not entered (`null`) | a list; `[]` means "none expected" | — |
| Advance amount (TND) | staff | not entered | entered | a staff member confirmed the payment |

Rules the API enforces:

- Missing information stays distinct from provided and from confirmed information: each item has a `status` of `MISSING`, `PROVIDED` or `CONFIRMED`.
- A well-formed phone number or e-mail is only provided, never confirmed. Confirmation is a separate staff action.
- A payment is confirmed only by `POST /appointments/{id}/payment-confirmation`. Uploading a document (a receipt, for example) never confirms it.
- The advance amount is typed by staff. Nothing in the system, and no agent tool, computes it from the patient's condition.
- Changing the trusted contact drops its confirmation; changing the advance amount drops the payment confirmation.
- Every change requires `APPOINTMENT_WRITE`, writes an audit event and publishes `AppointmentChecklistChanged`, which triggers a checklist recalculation for the appointment.

## Permissions

| Action | RECEPTIONIST | PRACTITIONER | CLINIC_MANAGER |
| --- | --- | --- | --- |
| List and read appointments | all in clinic | own only | all in clinic |
| List practitioners | yes | yes | yes |
| Create an appointment, change checklist data, confirm or revoke | yes | no (403) | yes |

An appointment of another clinic, or another practitioner's for a practitioner, is reported as `404 NOT_FOUND`.

## Appointment object

```json
{
  "id": "c0de…",
  "startsAt": "2026-11-20T08:30:00Z",
  "status": "SCHEDULED",
  "updatedAt": "2026-11-13T09:12:44.120Z",
  "patient": { "id": "5b1c…", "patientRef": "P-0042", "fullName": "Amel Test" },
  "practitioner": { "id": "9a2d…", "fullName": "Dr Karim Test" },
  "appointmentType": { "id": "a41f…", "code": "MRI", "label": "IRM" },
  "adminDetails": {
    "trustedContact": {
      "status": "CONFIRMED",
      "name": "Sami Test",
      "method": "PHONE",
      "value": "+216 20 000 000",
      "confirmation": { "by": "user id", "at": "2026-11-13T10:02:00Z" }
    },
    "expectedExaminations": { "status": "PROVIDED", "items": ["Bilan sanguin"] },
    "advance": {
      "status": "PROVIDED",
      "amount": 150.500,
      "currency": "TND",
      "paymentConfirmation": null
    }
  }
}
```

`status` is one of `SCHEDULED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.

## `GET /appointments`

Query parameters, all optional but the list must be bounded:

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | ISO-8601 instants; appointments starting in `[from, to)`. Both or neither; at most 92 days. |
| `patientId` | One patient's appointments. Required when there is no date range. |
| `practitionerId` | One practitioner. Ignored in favour of the caller's own id for practitioners (another id returns `[]`). |
| `status` | One or more statuses, repeated or comma-separated: `status=SCHEDULED,NO_SHOW`. |

`200 OK`: array of appointment objects, earliest first.

## `GET /appointments/{id}`

`200 OK` with the appointment object.

## `POST /appointments`

```json
{ "patientId": "5b1c…", "practitionerId": "9a2d…", "appointmentTypeId": "a41f…", "startsAt": "2026-11-20T08:30:00Z" }
```

All fields required. `201 Created` with a `Location: /api/v1/appointments/{id}` header and the appointment object, every checklist item `MISSING`. `404` if the patient, practitioner or type is not in the clinic.

## `PUT /appointments/{id}/admin-details`

Replaces every checklist field. `null` (or an omitted field) means "not provided".

```json
{
  "trustedContact": { "name": "Sami Test", "method": "PHONE", "value": "+216 20 000 000" },
  "expectedExaminations": ["Bilan sanguin", "IRM genou"],
  "advanceAmount": 150.5
}
```

- `trustedContact.method`: `PHONE` (same format check as a patient phone) or `EMAIL`.
- `expectedExaminations`: up to 30 non-blank entries of at most 200 characters.
- `advanceAmount`: TND, `>= 0`, at most 3 decimals.

`200 OK` with the appointment object. A request that changes nothing writes no audit event.

## Confirmations

| Endpoint | Effect | `409` when |
| --- | --- | --- |
| `POST /appointments/{id}/trusted-contact-confirmation` | Records that the current user reached the contact. | `TRUSTED_CONTACT_MISSING` |
| `DELETE /appointments/{id}/trusted-contact-confirmation` | Removes that confirmation. | — |
| `POST /appointments/{id}/payment-confirmation` | Records that the current user confirmed the advance was paid. | `ADVANCE_AMOUNT_MISSING` |
| `DELETE /appointments/{id}/payment-confirmation` | Removes that confirmation. | — |

Each returns `200 OK` with the appointment object. Confirming twice keeps the first confirmation.

## `GET /practitioners`

`200 OK`: the clinic's practitioners by name, `[{ "id", "fullName", "specialty" }]`.

## Audit

`APPOINTMENT_CREATED`, `APPOINTMENT_ADMIN_DETAILS_UPDATED` (changed field names, advance amount before and after, and whether a confirmation was cleared; never the contact details or examinations), `TRUSTED_CONTACT_CONFIRMED`, `TRUSTED_CONTACT_CONFIRMATION_REVOKED`, `PAYMENT_CONFIRMED`, `PAYMENT_CONFIRMATION_REVOKED`.

## Errors

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing create field; unbounded list or invalid range; unknown status; contact value not matching its method; invalid amount or examinations. |
| 401 | `UNAUTHENTICATED` | No or invalid token. |
| 403 | `FORBIDDEN` | Write by a practitioner. |
| 404 | `NOT_FOUND` | Appointment, patient, practitioner or type not in the clinic, or not visible. |
| 409 | `TRUSTED_CONTACT_MISSING`, `ADVANCE_AMOUNT_MISSING` | Nothing to confirm. |
