# Backend

Java 21 · Spring Boot 3 · Spring AI · PostgreSQL · Flyway

Scaffolded in **YOS-8**. Package layout:

```
com.clinicready
├── security/     CurrentUserService, AuthorizationService, JWT
├── clinic/
├── patient/
├── appointment/
├── document/     storage, extraction
├── readiness/    rules + deterministic engine
├── agent/        orchestrator, tools, AgentRun
├── followup/     tasks, drafts, approvals
└── audit/
```

See [CONTRIBUTING.md](../CONTRIBUTING.md) for backend rules.

## Run

From the repo root, `docker compose up --build` starts PostgreSQL and the API.

To run the API locally against the compose database (needs JDK 21):

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Health: `GET /actuator/health`.

## Test

```bash
./mvnw verify
```

Integration tests use Testcontainers, so Docker must be running.

## Migrations

Flyway scripts live in `src/main/resources/db/migration` as `V<n>__<description>.sql`. Hibernate only validates the schema (`ddl-auto: validate`).

## Demo data (profile `demo`)

Start with `SPRING_PROFILES_ACTIVE=demo` (or `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`) to seed a **fictional** clinic, "Clinique Démo Nabeul" (`com.clinicready.seed`):

- 2 practitioners (Dr Amel Ben Salem, Dr Walid Gharbi), 6 appointment types, 40 patients (24 FR, 16 AR with Arabic-script names), ~40 appointments around today (about 14 tomorrow, none on Sundays).
- One login per role, all with the password `password`: `reception@`, `manager@`, `dr.bensalem@`, `dr.gharbi@` + `nabeul-demo.test`.
- Idempotent: ids are derived from stable keys and rows are upserted, so every start converges to the same rows. Appointments are re-anchored to the current day each start, which also resets their status.
- Clearly fake: `.test` e-mails, patient refs `DEMO-001…040`, phones `+216 00 000 0NN` (not a valid Tunisian prefix). No national IDs or documents.
- Combine with `dev` if needed (`dev,demo`); the two use different clinics and e-mails.

Never enable `demo` against real data.
