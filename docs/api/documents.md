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
  "uploadedAt": "2026-11-11T19:24:04.949Z",
  "extractionStatus": "EXTRACTED",
  "extractionFailure": null,
  "extractedAt": "2026-11-11T19:24:05.312Z"
}
```

- `category`: `ID_DOCUMENT`, `INSURANCE_CARD`, `PRESCRIPTION`, `REFERRAL_LETTER`, `CONSENT_FORM`, `LAB_RESULT`, `IMAGING_REPORT`, `OTHER`.
- `source`: `STAFF_UPLOAD`, `EMAIL`, `SCAN`, `PATIENT_SUBMISSION`.
- `status`: `UPLOADED` on creation; later `NEEDS_REVIEW`, `VERIFIED`, `REJECTED` (set by upcoming features).
- `version`: starts at 1 per patient and category; each new upload of the same category is the next version. Earlier versions are kept.
- `contentType` is determined from the file's bytes, not from the client.
- `sha256` is the lowercase hex SHA-256 of the stored bytes.
- `extractionStatus`: whether the system could read the file's text (see [Text extraction](#text-extraction)). `PENDING` on creation, then `EXTRACTED` or `EXTRACTION_FAILED`. Independent of `status`.
- `extractionFailure`: set only when `extractionStatus` is `EXTRACTION_FAILED`: `NO_TEXT_LAYER`, `ENCRYPTED`, `UNREADABLE`, `FILE_MISSING` or `INTERNAL_ERROR`.
- `extractedAt`: when extraction finished, successfully or not; `null` while pending.

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

## `GET /documents/{id}/metadata`

`200 OK`: the fields read from the document, as corrected by staff. `fields` is empty and `updatedBy`, `updatedByName`, `updatedAt` are `null` until someone saves a field.

```json
{
  "documentId": "5d0a3f0e-…",
  "fields": { "expiryDate": "2027-03-01", "issuer": "CNAM" },
  "updatedBy": "6f1c…",
  "updatedByName": "Sarra Test",
  "updatedAt": "2026-11-13T19:24:04.949Z"
}
```

Field names: `documentDate`, `expiryDate` (ISO dates, `YYYY-MM-DD`), `issuer`, `documentNumber`, `holderName` (text, at most 200 characters). A field that is absent is not set.

## `PATCH /documents/{id}/metadata`

Corrects fields. Body: `{ "fields": { "issuer": "CNAM", "expiryDate": null } }`. Only the listed fields change; a `null` or blank value clears the field; values are trimmed. Returns the updated metadata (same shape as `GET`).

The editor (`updatedBy`) and the time (`updatedAt`) are saved with every change that alters a field. Re-sending the current values changes nothing: editor and time stay as they were. Each change is also written to the audit log (`DOCUMENT_METADATA_UPDATED`) with the names of the changed fields, never their values.

Allowed for receptionists, clinic managers and practitioners (practitioners only for documents of their own patients). All five fields are administrative, so none is withheld from practitioners.

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing `fields`, unknown field name, value over 200 characters, or a date that is not a real `YYYY-MM-DD` date. Nothing is saved. |
| 401 | `UNAUTHENTICATED` | No or invalid token. |
| 404 | `NOT_FOUND` | Document does not exist, belongs to another clinic, or is not visible to the user. |

## Text extraction

After an upload commits, the text of the file is extracted in the background (PDFBox). Poll `GET /documents/{id}` to see the result; it usually takes well under a second.

| Result | When |
| --- | --- |
| `EXTRACTED` | A PDF with a text layer. The text is stored server side for later features (metadata extraction, the agent). It is not returned by the API and never logged. |
| `EXTRACTION_FAILED` / `NO_TEXT_LAYER` | A JPEG or PNG, or a PDF made of scanned pages. Needs OCR (not available yet). |
| `EXTRACTION_FAILED` / `ENCRYPTED` | A password-protected PDF. |
| `EXTRACTION_FAILED` / `UNREADABLE` | A corrupt PDF. |
| `EXTRACTION_FAILED` / `FILE_MISSING` | The stored file is gone. |

A failed extraction is **not** a missing document: the document exists, the system just cannot read it. Readiness reports such a requirement as `NEEDS_REVIEW`, never `MISSING`.

Only the first 100 pages and 200 000 characters are kept. A document left `PENDING` (for example after a restart) is picked up again at startup and by a sweep every 5 minutes.

## AI metadata extraction

Once a document's text is extracted, a language model (Spring AI, Anthropic) is asked for three **documentary observations**: `category`, `documentDate` and `issuer`, plus its own `confidence` (0..1). The result is stored in `document_extraction`, one row per document, with the model name and the document id as the source reference. It is read server side with `DocumentService.aiExtraction(id)`; no endpoint exposes it yet.

The model's answer must be one JSON object that validates against `backend/src/main/resources/extraction/document-extraction.schema.json` (exactly the four keys, `category` from the category list above, date as a real `YYYY-MM-DD`, confidence in 0..1). Anything else is stored as `FAILED` / `INVALID_OUTPUT`; a failed model call as `FAILED` / `MODEL_ERROR`. Values are never repaired or guessed: `null` means "could not be identified".

| `reviewReason` | When | Effect |
| --- | --- | --- |
| `EXTRACTION_FAILED` | Invalid output or model error | Document `status` becomes `NEEDS_REVIEW` |
| `CATEGORY_UNKNOWN` | The model could not tell the category | same |
| `LOW_CONFIDENCE` | `confidence` below `DOCUMENTS_MIN_CONFIDENCE` (default `0.7`) | same |

Boundaries: these are observations only. They do not decide CNAM eligibility or coverage, authenticity, medical relevance or completeness, the advance amount, or whether payment is confirmed. A failed or flagged extraction is never a *missing* document, and observations are kept apart from staff corrections (`document_metadata`) and from the readiness engine's decision. Each observation points to its source document, so any decision that uses it can be traced back.

Without a model configured (`AI_CHAT_PROVIDER` unset or `none`), documents stay unclassified and nothing is flagged. Document text is sent to the configured provider (at most `clinicready.documents.classification.max-prompt-chars`, default 12 000); never use real patient documents with a provider you have not approved. Text, prompts and answers are never logged.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DOCUMENTS_STORAGE_PATH` | `./storage/documents` (`/data/documents` in Docker) | Where files are written. Mount a persistent volume. |
| `DOCUMENTS_MAX_SIZE` | `10MB` | Largest accepted file. |
| `DOCUMENTS_MAX_REQUEST_SIZE` | `12MB` | Spring multipart request limit; keep it above `DOCUMENTS_MAX_SIZE`. |
| `clinicready.documents.extraction.threads` | `2` | Background extraction workers. |
| `clinicready.documents.extraction.max-pages` | `100` | Pages read per PDF. |
| `clinicready.documents.extraction.max-chars` | `200000` | Characters stored per document. |
| `clinicready.documents.extraction.sweep-interval` | `PT5M` | How often documents stuck in `PENDING` are re-queued. |
| `AI_CHAT_PROVIDER` | `none` | Set to `anthropic` to enable AI extraction (also needs `ANTHROPIC_API_KEY`). |
| `ANTHROPIC_API_KEY` | empty | Provider key; never commit it. |
| `AI_EXTRACTION_MODEL` | `claude-haiku-5-5` | Model used for extraction. |
| `DOCUMENTS_MIN_CONFIDENCE` | `0.7` | Below this an observation is flagged for review. |
