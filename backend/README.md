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
