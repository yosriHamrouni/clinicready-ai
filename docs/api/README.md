# API contracts

All endpoints are under `/api/v1`. Contracts agreed in M0 go here (OpenAPI YAML or markdown), starting with:

- [x] Auth: `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` ([auth.md](auth.md))
- [ ] Patients: `GET /patients`, `POST /patients`, `GET /patients/{id}`
- [ ] Appointments: `GET /appointments`, `POST /appointments`, `GET /appointments/{id}`

Later: documents, requirements, readiness, agent runs, follow-ups, approvals, audit.

Changes to a contract go through a PR reviewed by both backend and frontend.
