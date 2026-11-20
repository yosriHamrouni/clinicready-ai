# API contracts

All endpoints are under `/api/v1`. Contracts agreed in M0 go here (OpenAPI YAML or markdown), starting with:

- [x] Auth: `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` ([auth.md](auth.md))
- [x] Patients: `GET /patients`, `POST /patients`, `GET /patients/{id}` ([patients.md](patients.md))
- [x] Appointments: `GET /appointments`, `POST /appointments`, `GET /appointments/{id}`, checklist data and confirmations, `GET /practitioners` ([appointments.md](appointments.md))

- [x] Documents: `POST /patients/{id}/documents`, `GET /patients/{id}/documents`, `GET /documents/{id}`, `GET /documents/{id}/content` ([documents.md](documents.md))

- [x] Requirements: `GET /appointment-types`, `GET|POST /appointment-types/{id}/requirements`, `PUT|DELETE /requirements/{id}` ([requirements.md](requirements.md))

- [x] Readiness: `POST /appointments/{id}/readiness-checks`, `GET /appointments/{id}/readiness` ([readiness.md](readiness.md))

- [x] Agent runs: `POST /agent-runs`, `GET /agent-runs/{id}` ([agent-runs.md](agent-runs.md))

- [x] Follow-ups and approvals: `GET /follow-up-tasks`, `GET /follow-up-tasks/{id}`, `POST /follow-up-tasks/{id}/approve|reject|delivered|resolve` ([follow-ups.md](follow-ups.md))

- [x] Audit: `GET /audit-events`, including the agent run trail via `runId` ([audit.md](audit.md))

Later: draft editing (YOS-31).

Changes to a contract go through a PR reviewed by both backend and frontend.
