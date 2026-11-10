# Contributing

Work is tracked in Linear (project **ClinicReady AI**, team key `YOS`).

## Workflow

1. Pick an issue assigned to you and move it to **In Progress**.
2. Create a branch from `main` named after the issue: `yos-23-readiness-engine` (Linear's "copy git branch name" works).
3. Keep pull requests small (aim for under ~400 changed lines).
4. Put `Fixes YOS-23` in the PR description so Linear closes the issue on merge.
5. CI must be green and the other person must approve. Squash-merge.

## Definition of done

- Unit tests for logic; an integration test for any new endpoint or agent tool.
- Authorization checked server-side for any new endpoint or tool.
- An audit event for any state change.
- Docs updated if behaviour or setup changed.

## Backend rules

- One package per feature (`patient`, `appointment`, `document`, `readiness`, `agent`, `followup`, `audit`, …) containing its entity, repository, service, controller and `dto/` records.
- Controllers never use repositories. Other features call this feature's **service**, never its repository.
- Every tenant-owned query is filtered by the current user's clinic from `CurrentUserService`, never from a request parameter. A record from another clinic returns 404.
- The readiness engine is plain Java: no Spring, no LLM.
- Only `FollowUpService` changes follow-up status, through the allowed transitions.
- Agent tools wrap services, take and return records, and never touch repositories. No tool can approve or execute an action.
- Schema changes only through Flyway migrations (`V<n>__<description>.sql`). Never use `ddl-auto=update`.
- Never log document contents, extracted text, full prompts or patient identity details.

## Frontend rules

- Server data through React Query; no server data in global state.
- One typed API client per backend area in `src/api/`.
- Mock the API with MSW from the agreed contracts in `docs/api/` while the backend is in progress.
- Use the shared `StatusBadge` for readiness statuses.

## API contracts

Contracts live in `docs/api/`. Any change to a contract goes through a PR reviewed by the other person.

## Data

Synthetic data only. Never commit real names, phone numbers, ID numbers or medical documents.
