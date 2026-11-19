# Local setup (Windows, PowerShell)

Branch: `yosrihamrouni/yos-40-architecture-docs`. Synthetic data only.

Every command marked **verified** was run on 2026-11-19 on Windows 11, Docker Desktop 29.6.2, Node 22.21.1, PowerShell 5.1.
Commands marked **not run** come from the repository but were not executed in this validation.

## 1. What the stack is

| Piece | Where it comes from | Port (host) |
| --- | --- | --- |
| PostgreSQL 16 | `docker-compose.yml` service `postgres` | `POSTGRES_PORT`, default 5432 |
| API (Spring Boot 3.5, Java 21) | `docker-compose.yml` service `api`, built from `backend/Dockerfile` | `API_PORT`, default 8080 |
| Frontend (React + Vite, served by nginx) | `docker-compose.yml` service `frontend`, built from `frontend/Dockerfile` | `FRONTEND_PORT`, default 3000 |

- **Migrations:** Flyway runs automatically when the API starts (`V1` to `V14`, `backend/src/main/resources/db/migration`). There is no separate migrate command.
- **Seed data:** Spring profiles, not scripts.
  - `dev`: one test clinic "Clinique Test Nabeul" and three users (`reception@clinic.test`, `doctor@clinic.test`, `manager@clinic.test`), Flyway repeatable script `db/seed-dev/R__dev_users.sql`. No appointments.
  - `demo`: "Clinique Démo Nabeul", 40 patients, ~42 appointments around today (about 14 tomorrow), 6 appointment types, users `reception@`, `manager@`, `dr.bensalem@`, `dr.gharbi@` + `nabeul-demo.test`. Re-anchored to today on every API start. **No requirement rules and no documents.**
  - Password for every seeded user: `password`.
- **Swagger / OpenAPI: not available.** There is no springdoc dependency. `GET /v3/api-docs` returns 404 (verified). The API contract is in [`docs/api/`](docs/api/README.md).
- **JDK:** this machine has no JDK 21, so the API runs in Docker and the tests run in a Maven container.

## 2. Environment variables

`.env` at the repo root (copy of `.env.example`) is read by Docker Compose. Never commit it.

| Variable | Needed | Notes |
| --- | --- | --- |
| `JWT_SECRET` | **yes** | At least 32 bytes. Compose refuses to start without it. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | no | Defaults `clinicready` / `clinicready` / `change-me`. |
| `POSTGRES_PORT` | if 5432 is taken | This machine runs a native PostgreSQL on 5432, so `.env` uses `5433`. |
| `API_PORT`, `FRONTEND_PORT` | no | 8080, 3000. |
| `SPRING_PROFILES_ACTIVE` | no | Compose default is `dev`. Use `dev,demo` for demo data. |
| `AI_CHAT_PROVIDER` | for a real agent | `none` (default) or `anthropic`. **Not passed by `docker-compose.yml`**, see section 6. |
| `ANTHROPIC_API_KEY` | for a real agent | Same. |
| `AI_EXTRACTION_MODEL` | no | Default `claude-haiku-5-5`. Used by the agent too, not only extraction. |
| `AI_TIMEOUT` | no | Per model call, default `30s`. |
| `JWT_COOKIE_SECURE` | no | Default `true` (fine on http://localhost). |

`.env.example` still lists `LLM_PROVIDER` / `LLM_API_KEY`. The code does not read them; it reads `AI_CHAT_PROVIDER` / `ANTHROPIC_API_KEY`.

Generate a JWT secret without OpenSSL:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

## 3. Start everything (Docker Compose)

Run from the repo root.

```powershell
Set-Location C:\dev\clinicready-ai
```

First time only:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Then put a `JWT_SECRET` in `.env`, and `POSTGRES_PORT=5433` if a local PostgreSQL already listens on 5432 (check: `netstat -ano | Select-String ':5432 '`).

Start with demo data (**verified** for `api`; the stack was already built and running, so `--build` was not re-run):

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,demo'
```

```powershell
docker compose up -d --build
```

The shell variable overrides `.env`. It lasts until you close the window.

Wait for the API (**verified**, UP after ~65 s on this machine):

```powershell
do { Start-Sleep 5; try { $s = (Invoke-RestMethod http://localhost:8080/actuator/health).status } catch { $s = 'down' } } until ($s -eq 'UP'); "API $s"
```

Migrations and seed check (**verified**: latest version 14, 42 demo appointments):

```powershell
docker exec clinicready-ai-postgres-1 psql -U clinicready -d clinicready -c "select version, success from flyway_schema_history order by installed_rank desc limit 3"
```

```powershell
docker exec clinicready-ai-postgres-1 psql -U clinicready -d clinicready -c "select min(starts_at)::date, max(starts_at)::date, count(*) from appointment"
```

Only infrastructure, e.g. to run the API from an IDE with JDK 21 (**not run**):

```powershell
docker compose up -d postgres
```

```powershell
$env:DB_PORT = '5433'; $env:JWT_SECRET = '<at least 32 bytes>'; Set-Location backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

`application.yml` reads `DB_PORT` (not `POSTGRES_PORT`).

Reset the database (**destroys all local data** in the `pgdata` and `documents` volumes, **not run**):

```powershell
docker compose down -v
```

Stop without losing data:

```powershell
docker compose down
```

## 4. Access

| What | URL | Status |
| --- | --- | --- |
| App (nginx, proxies `/api/`) | http://localhost:3000 | **verified** 200, login through it works |
| API health | http://localhost:8080/actuator/health | **verified** `UP` |
| API base | http://localhost:8080/api/v1 | **verified** |
| Swagger UI | none | `/swagger-ui/index.html` returns 401 (no such page; security answers first) |

Frontend dev server with hot reload instead of the nginx container (**not run** live; `typecheck` and `lint` verified):

```powershell
Set-Location frontend; npm install; npm run dev
```

Opens http://localhost:5173 and proxies `/api` to http://localhost:8080 (`VITE_DEV_API_TARGET` to change).

## 5. Call the API from PowerShell

PowerShell 5.1 note: `@(Invoke-RestMethod …)` wraps a JSON array as **one** item. Assign first, then unroll, as `Get-Api` below does.

```powershell
$api = 'http://localhost:8080/api/v1'
function Get-Token($email) { (Invoke-RestMethod -Method Post -Uri "$api/auth/login" -ContentType 'application/json' -Body (@{ email = $email; password = 'password' } | ConvertTo-Json)).accessToken }
function Get-Api($path, $h) { $x = Invoke-RestMethod -Uri "$api$path" -Headers $h; $x | ForEach-Object { $_ } }
$h = @{ Authorization = "Bearer $(Get-Token 'reception@nabeul-demo.test')" }
$from = [uri]::EscapeDataString((Get-Date).Date.AddDays(1).ToUniversalTime().ToString('o'))
$to = [uri]::EscapeDataString((Get-Date).Date.AddDays(2).ToUniversalTime().ToString('o'))
$appts = @(Get-Api "/appointments?from=$from&to=$to" $h); "tomorrow: $($appts.Count)"
```

**Verified**: 14 appointments tomorrow for the receptionist, 7 for `dr.gharbi@nabeul-demo.test`.
`GET /appointments` without `from`/`to` or `patientId` is `400` by design.

## 6. Real Anthropic model (agent runs)

Without a provider every agent run ends `FAILED` / `No model is configured` (**verified**). `docker-compose.yml` does not pass the AI variables to `api`, so add them with a local override file. Keep the key out of every file.

1. Create `docker-compose.anthropic.yml` in the repo root and keep it out of git:

   ```yaml
   services:
     api:
       environment:
         AI_CHAT_PROVIDER: anthropic
         ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:?Set ANTHROPIC_API_KEY in this shell}
         AI_EXTRACTION_MODEL: ${AI_EXTRACTION_MODEL:-claude-haiku-5-5}
   ```

   ```powershell
   Add-Content .git\info\exclude 'docker-compose.anthropic.yml'
   ```

2. Put the key in this shell only, typed hidden (not in `.env`, not in history):

   ```powershell
   $sec = Read-Host 'Anthropic API key' -AsSecureString; $env:ANTHROPIC_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)); Remove-Variable sec
   ```

3. Recreate the API with the override:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = 'dev,demo'; docker compose -f docker-compose.yml -f docker-compose.anthropic.yml up -d api
   ```

4. Start a run and poll it (section 5 variables first):

   ```powershell
   $run = Invoke-RestMethod -Method Post -Uri "$api/agent-runs" -Headers $h -ContentType 'application/json' -Body (@{ request = "Prepare tomorrow's appointments for Dr. Walid Gharbi" } | ConvertTo-Json)
   do { Start-Sleep 3; $r = Invoke-RestMethod -Uri "$api/agent-runs/$($run.runId)" -Headers $h } while ($r.status -in 'QUEUED','RUNNING'); $r | ConvertTo-Json -Depth 8
   ```

5. When done, drop the key and recreate the API without it:

   ```powershell
   Remove-Item Env:ANTHROPIC_API_KEY; docker compose up -d api
   ```

Credential rules:

- `docker compose config` with the override **prints the key in clear text**. Do not run it, or redact its output.
- `docker inspect clinicready-ai-api-1` also shows the container environment. Do not paste its output anywhere.
- Prompts and logs carry ids and categories only (by design).
- Checked with a dummy key only: the override merges correctly. **A real model run was not executed** (no key on this machine).

Expected behaviour and known limits: [LOCAL_TEST_PLAN.md](LOCAL_TEST_PLAN.md), section 7.

## 7. Tests

Backend. Testcontainers needs Docker; runs in a Maven container because there is no local JDK 21. Results in LOCAL_TEST_PLAN.md.

PowerShell form (**not run** in this form):

```powershell
Set-Location C:\dev\clinicready-ai\backend; docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "C:/dev/clinicready-ai/backend:/app" -v clinicready-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

Git Bash form (**verified**, ~25 min on this machine):

```bash
cd /c/dev/clinicready-ai/backend && MSYS_NO_PATHCONV=1 docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v "C:/dev/clinicready-ai/backend:/app" -v clinicready-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

With a local JDK 21 instead (**not run**): `.\mvnw.cmd verify` in `backend`.

Frontend (**verified**):

```powershell
Set-Location C:\dev\clinicready-ai\frontend; npm run typecheck; npm run lint; npm test
```

## 8. Known issues and missing configuration

Found on 2026-11-19. None was fixed (no application code changed).

| # | Issue | Impact | Evidence |
| --- | --- | --- | --- |
| K1 | No Swagger / OpenAPI (no springdoc dependency) | No interactive API explorer; use `docs/api/` | `/v3/api-docs` 404 (run) |
| K2 | `docker-compose.yml` does not pass `AI_CHAT_PROVIDER` / `ANTHROPIC_API_KEY` to `api` | Agent runs always `FAILED` "No model is configured" in Compose; document extraction stays off | compose file; run T2.2 |
| K3 | `.env.example` documents `LLM_PROVIDER` / `LLM_API_KEY`, which the code never reads | Setting them does nothing | `application.yml` reads `AI_CHAT_PROVIDER`, `ANTHROPIC_API_KEY` |
| K4 | Compose default profile is `dev`; demo data needs `dev,demo` and is re-anchored only when the API starts with `demo` | Without it, "tomorrow" is empty or stale (found stale: 9–18 Nov) | run |
| K5 | Demo seed has no requirement rules and no documents | Readiness only shows built-ins as `NEEDS_REVIEW`; no `MISSING`, so the agent creates no follow-ups until a manager adds a rule | run (rules = 0) |
| K6 | Agent inherits `max-tokens: 400` from the extraction settings | Reports for many appointments may be truncated and fail (risk, code reading only) | `application.yml`, `AgentOrchestrator` options |
| K7 | 15 tool calls per run vs 14 appointments tomorrow | Whole-clinic "prepare tomorrow" will likely hit `TOOL_LIMIT_EXCEEDED` (risk, code reading only) | `AiProperties` |
| K8 | No separate agent model setting; agent uses `AI_EXTRACTION_MODEL` (Haiku) | Cannot pick a stronger model for the agent alone | `application.yml` |
| K9 | Frontend has no UI for readiness, agent runs or follow-ups (placeholders); no API client for them | Backend features of YOS-24..37 reachable by API only (YOS-26, 30, 31, 34, 36 are Amine's) | `frontend/src/api/endpoints/`, `AgentWorkspacePage.tsx`, `FollowUpsPage.tsx` |
| K10 | No REST endpoint creates a follow-up task; only the agent tool does | Manual approval tests need a real model run first | `FollowUpController` |
| K11 | No audit read endpoint; no audit event for agent runs or document uploads (runs are in `agent_run` / `agent_action`) | Audit checked in SQL | `AuditAction` enum |
| K12 | Native PostgreSQL on this machine holds port 5432 | Compose needs `POSTGRES_PORT=5433` (already in `.env`) | `netstat` |
| K13 | `frontend/README.md` says `depends_on: api` is commented out; it is active | Doc drift only | `docker-compose.yml` |
| K14 | Frontend Vitest suite flaky on this machine (failure count varied between runs) | See LOCAL_TEST_PLAN.md section 1 | run |
| K15 | Untracked `bash.exe.stackdump` (Git Bash crash dump) in the repo root | Noise; safe to delete, not in `.gitignore` | `git status` |
