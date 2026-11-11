# Documents API

Base path: `/api/v1`. Authentication as in [auth.md](auth.md). Errors use the common `{ "code", "message", "details" }` body.

A document is a PDF, JPEG or PNG attached to a patient. The file is stored **outside the database** (a volume, `DOCUMENTS_STORAGE_PATH`); the database holds metadata and a SHA-256 checksum. There is no public file URL: bytes are only returned by `GET /documents/{id}/content`, after authentication and the permission and patient checks below.

## Permissions

| Action | RECEPTIONIST | CLINIC_MANAGER | PRACTITIONER |
| --- | --- | --- | --- |
| Upload | yes | yes | no (403) |
| List / read metadata / download | yes | yes | only documents of patients they have an appointment with |

A document or patient of another clinic, or one the user may not see, is reported as `404 NOT_FOUND`.

## Document object

```json
{
  "id": "5d0a3f0e-…",
  "patientId": "9b1c…",
  "category": "PRESCRIPTION",
  "source": "STAFF_UPLOAD",
  "status": "UPLOADED",
  "version": 2,
  "originalFilename": "ordonnance.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 48213,
  "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "uploadedBy": "6f1c…",
  "uploadedAt": "2026-11-11T19:24:04.949Z"
}
```

- `category`: `ID_DOCUMENT`, `INSURANCE_CARD`, `PRESCRIPTION`, `REFERRAL_LETTER`, `CONSENT_FORM`, `LAB_RESULT`, `IMAGING_REPORT`, `OTHER`.
- `source`: `STAFF_UPLOAD`, `EMAIL`, `SCAN`, `PATIENT_SUBMISSION`.
- `status`: `UPLOADED` on creation; later `NEEDS_REVIEW`, `VERIFIED`, `REJECTED` (set by upcoming features).
- `version`: starts at 1 per patient and category; each new upload of the same category is the next version. Earlier versions are kept.
- `contentType` is determined from the file's bytes, not from the client.
- `sha256` is the lowercase hex SHA-256 of the stored bytes.

## `POST /patients/{patientId}/documents`

`multipart/form-data`:

| Part | Required | Notes |
| --- | --- | --- |
| `file` | yes | PDF, JPEG or PNG, at most 10 MB (`DOCUMENTS_MAX_SIZE`). |
| `category` | yes | One of the categories above. |
| `source` | no | Defaults to `STAFF_UPLOAD`. |

`201 Created` with a `Location: /api/v1/documents/{id}` header and the document object.

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing `file` or `category`, unknown category or source, empty file. |
| 401 | `UNAUTHENTICATED` | No or invalid token. |
| 403 | `FORBIDDEN` | Role may not upload (practitioner). |
| 404 | `NOT_FOUND` | Patient does not exist, belongs to another clinic, or is not visible to the user. |
| 409 | `UPLOAD_CONFLICT` | Another upload for the same patient and category took the version number; retry. |
| 413 | `FILE_TOO_LARGE` | Larger than the size limit. |
| 415 | `UNSUPPORTED_FILE_TYPE` | Content is not PDF/JPEG/PNG, or the declared `Content-Type` disagrees with the content. |

## `GET /patients/{patientId}/documents`

`200 OK`: array of document objects, grouped by category, newest version first. `404` as above.

## `GET /documents/{id}`

`200 OK`: one document object. `404` if missing, in another clinic, or not visible to the user.

## `GET /documents/{id}/content`

`200 OK` with the file bytes. Headers: `Content-Type` (the detected type), `Content-Disposition: inline; filename*=…`, `ETag: "<sha256>"`, `Cache-Control: no-store, private`, `X-Content-Type-Options: nosniff`. Same errors as above.

The browser sends the session cookie automatically, so `<img src>` / `<iframe src>` pointing at this URL work; the URL cannot be shared with someone who is not logged in.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DOCUMENTS_STORAGE_PATH` | `./storage/documents` (`/data/documents` in Docker) | Where files are written. Mount a persistent volume. |
| `DOCUMENTS_MAX_SIZE` | `10MB` | Largest accepted file. |
| `DOCUMENTS_MAX_REQUEST_SIZE` | `12MB` | Spring multipart request limit; keep it above `DOCUMENTS_MAX_SIZE`. |
