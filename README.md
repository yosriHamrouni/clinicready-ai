# ClinicReady AI

> Prepare every patient file before the appointment.

ClinicReady AI is an administrative assistant for Tunisian private clinics. A tool-using AI agent checks upcoming appointments against clinic-defined document rules, explains every gap with evidence, and drafts follow-ups that a staff member approves before anything happens.

It is **not** a diagnostic tool. It never gives medical advice, never decides whether a patient is fit for a consultation, and never sends a message without human approval.

> **Status:** M0 — validation and setup. Synthetic data only. Not for use with real patient data.

## The problem

Receptionists in private clinics check patient files by hand before appointments, across paper folders, PDFs and messaging apps. Missing referrals or forms are often discovered at the desk, on the day.

## The solution

- **Deterministic readiness engine** — clinic managers define which documents each appointment type requires; the engine marks each one `PRESENT`, `MISSING`, `NEEDS_REVIEW`, `EXPIRED` or `NOT_APPLICABLE`, with evidence.
- **Agent** — answers requests like *"Prepare tomorrow's appointments for Dr. Ben Salem"* by calling a small set of controlled tools, then reports what is ready, what is missing and why.
- **Human approval** — the agent can only read and draft. Every follow-up goes through a separate approval API before it is executed.
- **Audit trail** — every agent run, tool call, approval and state change is recorded.

## Architecture

```
React + TypeScript UI
        │
Spring Boot REST API  (auth · clinic-scoped authorization)
   ┌────┴─────────────────────┐
Spring AI agent             Approval API
(read + draft tools only)   (verifies approval, then executes)
   └────┬─────────────────────┘
Domain services + PostgreSQL  (rules engine · approval state · audit)
```

See [`docs/architecture`](docs/architecture) for details.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React, TypeScript, Vite |
| Backend | Java 21, Spring Boot 3 |
| Agent | Spring AI (tool calling) |
| Database | PostgreSQL, Flyway |
| Documents | Apache Tika / PDFBox |
| Security | Spring Security + JWT |
| Testing | JUnit, Spring Boot Test, Testcontainers |
| Delivery | Docker Compose, GitHub Actions |

## Repository layout

```
backend/    Spring Boot API, agent and domain services
frontend/   React + TypeScript app
docs/       PRD, API contracts, architecture, decisions
eval/       Evaluation test set and expected outcomes
```

## Getting started

> The backend and frontend are scaffolded in M1 (YOS-8, YOS-9). For now only the database runs.

```bash
cp .env.example .env
docker compose up -d postgres
```

## Roadmap

| Milestone | Dates (2026) | Deliverable |
| --- | --- | --- |
| M0 Validation and setup | 10–15 Nov | Interviews, frozen scope, repo |
| M1 Foundation | 16–20 Nov | Auth, clinic isolation, seed data |
| M2 Appointments and documents | 23–27 Nov | Patient files with extracted metadata |
| M3 Readiness engine | 30 Nov–4 Dec | Reliable readiness results without an LLM |
| M4 Agentic AI | 7–11 Dec | First end-to-end agent run |
| M5 Approvals and reliability | 14–18 Dec | Safe, traceable, tested workflow |
| M6 Portfolio polish | 21–25 Dec | Demo, docs, video, evaluation report |

## Evaluation

Results will be measured on a fixed synthetic test set of 40 appointments and published in [`eval/`](eval) — including failures.

## Safety boundaries

- The agent has no direct database access and no more permissions than the user who started the run.
- No tool can approve or execute a follow-up; only the approval API can, after a recorded human decision.
- Readiness is decided by deterministic rules, never by the LLM.
- Prompts and logs carry IDs and document categories, not document contents or identity details.

## Team

- Yosri Hamrouni — backend, security, agent
- Amine Mkacher — frontend, document pipeline, data and evaluation, infrastructure

## License

[MIT](LICENSE)
