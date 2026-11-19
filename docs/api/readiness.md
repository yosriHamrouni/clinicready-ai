# Readiness API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

Readiness is an **administrative completeness check**. It is not a medical decision and does not guarantee coverage or payment. Statuses and aggregate rules: [docs/prd.md](../prd.md#readiness-statuses).

## What is checked

Each appointment is checked against:

- every requirement rule of its appointment type ([requirements.md](requirements.md)), matched against the newest version of each of the patient's document categories (a replaced version is no longer evidence);
- two built-in mandatory requirements: `EXPECTED_EXAMINATIONS` (the list is entered, `[]` counts) and `ADVANCE_PAYMENT` (amount entered and payment confirmed by staff, or amount `0`).

## Permissions

Same as reading the appointment: receptionists and managers for every appointment of the clinic, practitioners for their own only. Anything else is `404 NOT_FOUND`. Evidence references are ids and metadata; the file itself is read through `GET /documents/{id}/content` with its own checks.

## Recalculation

The stored result is recalculated, in the same transaction, after:

- appointment creation, administrative data changes, payment or trusted-contact confirmation and their revocation (`AppointmentChecklistChanged`);
- a document upload or new version, a metadata correction, a text extraction result, an AI observation or review flag (`PatientDocumentsChanged`, for the patient's scheduled appointments from yesterday on);
- a requirement rule created, changed or deleted (scheduled appointments of the type from yesterday on).

## `POST /appointments/{id}/readiness-checks`

Runs the checks now and stores the result. `200 OK` with the readiness object.

## `GET /appointments/{id}/readiness`

`200 OK` with the latest stored result. `404 NOT_FOUND` if the appointment was never checked (for example, created before this feature): run `POST …/readiness-checks` first.

## Readiness object

```json
{
  "appointmentId": "c0de…",
  "status": "INCOMPLETE",
  "notice": "Administrative completeness check only. It is not a medical decision and does not guarantee coverage or payment.",
  "evaluatedAt": "2026-11-17T09:12:44.120Z",
  "incompleteReasons": ["No CONSENT_FORM document found in the sources checked: …"],
  "reviewReasons": ["Advance payment confirmation pending"],
  "checks": [
    {
      "ruleId": "7f1e…",
      "requirement": "DOCUMENT",
      "documentCategories": ["CONSENT_FORM"],
      "mandatory": true,
      "applicable": true,
      "status": "MISSING",
      "reason": "No CONSENT_FORM document found in the sources checked: …",
      "remainingAction": "Obtain and upload a CONSENT_FORM document",
      "requiresHumanReview": false,
      "documents": [],
      "confirmations": []
    },
    {
      "ruleId": null,
      "requirement": "ADVANCE_PAYMENT",
      "documentCategories": [],
      "mandatory": true,
      "applicable": true,
      "status": "PRESENT",
      "reason": "Advance payment confirmed by staff",
      "remainingAction": null,
      "requiresHumanReview": false,
      "documents": [],
      "confirmations": [
        { "type": "PAYMENT_CONFIRMED", "confirmedBy": "user id", "confirmedByName": "Sami Test", "confirmedAt": "2026-11-16T15:02:00Z" }
      ]
    }
  ]
}
```

- `incompleteReasons` (mandatory `MISSING` / `EXPIRED`) and `reviewReasons` (mandatory `NEEDS_REVIEW`) are listed separately, so review reasons stay visible when the aggregate is `INCOMPLETE`. Optional checks are listed in `checks` but never in these lists.
- `documents` are the stored documents the status is based on: `{ documentId, category, version, status, uploadedAt }`. For a rejected document they are the rejected ids.
- `confirmations` are administrative decisions by a named staff member, never AI observations.
- `ruleId` is `null` for the built-in requirements.
