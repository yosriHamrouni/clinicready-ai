# Backend

Java 21 · Spring Boot 3 · Spring AI · PostgreSQL · Flyway

Scaffolded in **YOS-8**. Planned package layout:

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
