# API contracts

All endpoints are under `/api/v1`. Contracts agreed in M0 go here (OpenAPI YAML or markdown), starting with:

- [x] Auth: `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` ([auth.md](auth.md))
- [x] Patients: `GET /patients`, `POST /patients`, `GET /patients/{id}` ([patients.md](patients.md))
- [x] Appointments: `GET /appointments`, `POST /appointments`, `GET /appointments/{id}`, checklist data and confirmations, `GET /practitioners` ([appointments.md](appointments.md))

- [x] Documents: `POST /patients/{id}/documents`, `GET /patients/{id}/documents`, `GET /documents/{id}`, `GET /documents/{id}/content` ([documents.md](documents.md))

- [x] Requirements: `GET /appointment-types`, `GET|POST /appointment-types/{id}/requirements`, `PUT|DELETE /requirements/{id}` ([requirements.md](requirements.md))

Later: readiness, agent runs, follow-ups, approvals, audit.

Changes to a contract go through a PR reviewed by both backend and frontend.
