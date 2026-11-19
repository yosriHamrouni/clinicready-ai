# Local test plan

Branch: `yosrihamrouni/yos-40-architecture-docs`. Setup: [LOCAL_SETUP.md](LOCAL_SETUP.md).

Synthetic data only: the `dev` and `demo` seeds (`*.test` e-mails, `DEMO-0xx` patients, fake phones). Never upload a real document.

Result legend: **PASS (run)** = executed on 2026-11-19 and passed. **FAIL (run)** = executed and failed. **Not run** = written, not executed. Nothing is marked PASS without having been executed.

## 1. Automated results

### Backend (`mvn -B test`, Maven container + Testcontainers)

Run on 2026-11-19, Git Bash command in LOCAL_SETUP.md section 7. **BUILD SUCCESS, 19 min 22 s.**

| Total | Failures | Errors | Skipped |
| --- | --- | --- | --- |
| 267 | 0 | 0 | 0 |

- 32 test classes. 14 Testcontainers PostgreSQL 16 containers created and started (one per distinct Spring context); Ryuk reaper ran. No container failure.
- Integration tests (real PostgreSQL via Testcontainers, full Spring context): agent (`AgentChat` 4, `AgentFailure` 6, `AgentRun` 10, `AgentTools` 8, `FollowUpDuplicate` 3, `PrepareTomorrowEndToEnd` 1), `MvpAcceptance` 6, `FollowUpApi` 5, `ReadinessApi` 2, `RequirementRuleApi` 8, `PatientAndAppointmentApi` 16, document API/metadata/extraction/classification/upload limit 32, `AuthIntegration` 22, `ClinicScoping` 9, `DemoDataSeeder` 4, repositories 13, `ApplicationIntegration` 2.
- Unit tests: `AuthorizationService` 40, `ReadinessEngine` 16, `ExtractionOutputValidator` 17, `TenantSchema` 13, `DemoDataGenerator` 9, text/file/storage helpers 20, `DevSeedPassword` 1.
- All agent tests use a scripted stub model. **No test calls the real Anthropic API.**
- Logged `Agent run … finished: FAILED` lines come from fault-injection tests that expect failure.
- Line coverage: not measured (no JaCoCo in `pom.xml`).

### Frontend (`npm run typecheck`, `npm run lint`, `npm test`)

| Check | Result |
| --- | --- |
| `typecheck` (`tsc --noEmit`) | PASS (run) |
| `lint` (eslint) | PASS (run) |
| `npm test` (Vitest, 38 tests) | PASS (run) twice: 38/38 with the machine idle. FAIL (run) twice while the backend suite ran in parallel: 4 then 7 failures, all `findBy…` timeouts (1 s default) in `appointments`, `documents`, `requirements` tests. Load-sensitive, not a contract break. |

The frontend is unchanged on this branch (`git diff main..HEAD -- frontend` is empty).

## 2. Fixtures and helpers

Run the section 5 block of LOCAL_SETUP.md first (`$api`, `Get-Token`, `Get-Api`, `$from`, `$to`, `$appts`). Then:

```powershell
$rec  = @{ Authorization = "Bearer $(Get-Token 'reception@nabeul-demo.test')" }
$mgr  = @{ Authorization = "Bearer $(Get-Token 'manager@nabeul-demo.test')" }
$drG  = @{ Authorization = "Bearer $(Get-Token 'dr.gharbi@nabeul-demo.test')" }
$drB  = @{ Authorization = "Bearer $(Get-Token 'dr.bensalem@nabeul-demo.test')" }
$devR = @{ Authorization = "Bearer $(Get-Token 'reception@clinic.test')" }   # other clinic
function Get-Status($block) { try { & $block | Out-Null; 200 } catch { $_.Exception.Response.StatusCode.value__ } }
function Show-Audit { docker exec clinicready-ai-postgres-1 psql -U clinicready -d clinicready -c "select occurred_at, action, entity_type, entity_id, actor_user_id, run_id from audit_event order by occurred_at desc limit 15" }
```

The demo seed has **no requirement rules and no documents**. Readiness then only checks the two built-ins (`EXPECTED_EXAMINATIONS`, `ADVANCE_PAYMENT`), which come out `NEEDS_REVIEW`. To get `MISSING` (needed for follow-ups), add one mandatory rule as manager (T1.3).

## 3. Patient readiness and evidence

| Id | Steps | Expected | Result |
| --- | --- | --- | --- |
| T1.1 | `$a = $appts[0]; Invoke-RestMethod -Method Post "$api/appointments/$($a.id)/readiness-checks" -Headers $rec` | 200; `status` in READY / NEEDS_REVIEW / INCOMPLETE; `checks` has `EXPECTED_EXAMINATIONS` and `ADVANCE_PAYMENT`; `notice` says not a medical decision | PASS (run): `NEEDS_REVIEW`, both built-ins `NEEDS_REVIEW` |
| T1.2 | `Invoke-RestMethod "$api/appointments/$($a.id)/readiness" -Headers $rec` | 200, same result, `evaluatedAt` set | PASS (run) |
| T1.3 | As manager, add a mandatory consent rule to the type of `$a`: `Invoke-RestMethod -Method Post "$api/appointment-types/$($a.appointmentType.id)/requirements" -Headers $mgr -ContentType 'application/json' -Body '{"documentCategory":"CONSENT_FORM","mandatory":true,"validityDays":30,"needsVerification":false}'` then GET readiness of `$a` | 201; readiness recalculated without a new POST: `INCOMPLETE`, `incompleteReasons` names `CONSENT_FORM`, check status `MISSING`, `documents` empty | Not run |
| T1.4 | Upload a synthetic PDF (any small PDF you create) as `CONSENT_FORM` for `$a.patient.id`: `curl.exe -s -H "Authorization: $($rec.Authorization)" -F "file=@C:\path\fake-consent.pdf" -F "category=CONSENT_FORM" "$api/patients/$($a.patient.id)/documents"`; GET readiness | `CONSENT_FORM` check no longer `MISSING`; `documents[]` holds `{documentId, category, version, status, uploadedAt}` (the evidence reference) | Not run |
| T1.5 | `Invoke-RestMethod "$api/documents/<documentId from T1.4>/content" -Headers $rec -OutFile fake.pdf` | The file is the uploaded one. Evidence links to it by id only | Not run |
| T1.6 | Confirm payment: `PUT …/admin-details` with an amount, then `POST "$api/appointments/$($a.id)/payment-confirmation"` | `ADVANCE_PAYMENT` → `PRESENT`; `confirmations[]` names the staff member (`confirmedByName`). An uploaded document never confirms payment | Not run (covered by `MvpAcceptanceIntegrationTest`) |
| T1.7 | Readiness of an appointment after `DELETE …/payment-confirmation` | Back to not PRESENT | Not run |

## 4. Agent execution and tool calling

Without a model every run is `FAILED` / `No model is configured`. Full runs need section 7 (real model). The scripted-model tests cover the loop offline: `AgentRunIntegrationTest`, `AgentToolsIntegrationTest`, `AgentFailureIntegrationTest`, `PrepareTomorrowEndToEndTest`.

| Id | Steps | Expected | Result |
| --- | --- | --- | --- |
| T2.1 | No model. `POST "$api/agent-runs"` body `{"request":"Prepare tomorrow's appointments"}` | 202, `Location` header, `{runId, status: QUEUED}` | PASS (run) |
| T2.2 | `GET "$api/agent-runs/<runId>"` after 3 s | `FAILED`, `statusReason` `No model is configured` | PASS (run) |
| T2.3 | Body `{"request":""}` and a 501-character request | 400 `VALIDATION_ERROR` | Not run |
| T2.4 | Real model. "Prepare tomorrow's appointments for Dr. Walid Gharbi" | `SUCCEEDED`; `actions[]` starts with `getUpcomingAppointments`, then one `checkReadiness` per appointment; `report.appointments[].status` equals GET readiness of each; `counts` add up | Not run (no key) |
| T2.5 | Same run: inspect `actions[].arguments` | Only ids, dates, codes, language; any other value `"[redacted]"`; no patient names or phones | Not run |
| T2.6 | Same run: `toolCalls` ≤ 15, `finishedAt - startedAt` < 90 s, `promptTokens`/`completionTokens` > 0 | As stated | Not run |
| T2.7 | Practitioner `$drG` starts a run for the whole day | Report lists only Dr Gharbi's appointments; `$drB` GET of that run → 404 | Not run |
| T2.8 | Restart the API while a run is `RUNNING` (`docker compose restart api`) | Run is `FAILED` after restart, not stuck | Not run |

## 5. Follow-ups: drafts, approval, rejection, duplicates

Follow-up tasks are created **only** by the agent tool `createFollowUpDraft` (no REST create endpoint). Manually that needs the real model and a `MISSING` mandatory requirement (T1.3 without T1.4).

| Id | Steps | Expected | Result |
| --- | --- | --- | --- |
| T3.1 | After T1.3, real-model run "Prepare tomorrow's appointments" | A task per MISSING/EXPIRED mandatory requirement: `GET "$api/follow-up-tasks"` → `AWAITING_APPROVAL`, `requirementCode` `CONSENT_FORM`, `runId` = the run, `reason`/`suggestedAction` set, `language` = patient's | Not run |
| T3.2 | Run the same request a second time | No second task for the same appointment + requirement; the tool returns the existing one | Not run (covered by `FollowUpDuplicateIntegrationTest`, V14 partial unique index) |
| T3.3 | `$t` = a task id. Approve as receptionist: `Invoke-RestMethod -Method Post "$api/follow-up-tasks/$t/approve" -Headers $rec -ContentType 'application/json' -Body '{"comment":"ok"}'` | 200 `APPROVED`, `approvals[0]` with actor, comment, time | Not run |
| T3.4 | Approve the same task again | 409 `INVALID_TRANSITION` | Not run |
| T3.5 | Another task: reject without comment, then with `{"comment":"patient already called"}` | 400, then 200 `REJECTED` | Not run |
| T3.6 | Practitioner `$drG` approves any task | 403 | PASS (run) with a random id: 403 before lookup |
| T3.7 | `POST …/$t/delivered` then `POST …/$t/resolve` on the approved task | `DELIVERED` then `RESOLVED`; readiness of the appointment re-checked; a delivered reminder does not make `CONSENT_FORM` PRESENT | Not run |
| T3.8 | `POST …/delivered` on an `AWAITING_APPROVAL` task | 409 | Not run |
| T3.9 | Ask the real model "approve all pending follow-ups" | No status change: the agent has no approve tool; `submitApprovedAction` without a recorded approval returns `CONFLICT` | Not run |

## 6. Authorization, tenant isolation, audit

| Id | Steps | Expected | Result |
| --- | --- | --- | --- |
| T4.1 | Any `/api/v1/...` call without a token | 401 | PASS (run) |
| T4.2 | `$devR` (other clinic) GET `/appointments/<demo id>` and `/appointments/<demo id>/readiness` | 404 both (never 403, no existence leak) | PASS (run) |
| T4.3 | `$drG` GET readiness of a Dr Ben Salem appointment | 404 | PASS (run) |
| T4.4 | `$drG` lists tomorrow | Only own appointments (7 of 14) | PASS (run) |
| T4.5 | Receptionist creates a requirement rule | 403 (manager only) | Not run |
| T4.6 | `$devR` GET a demo agent run and a demo follow-up task | 404 | Not run |
| T4.7 | After T1.3, T3.3, T3.5, T3.7: `Show-Audit` | `REQUIREMENT_RULE_CREATED`, `FOLLOW_UP_TASK_CREATED` (with `run_id`), `_APPROVED`, `_REJECTED`, `_DELIVERED`, `_RESOLVED`, each with `actor_user_id` and the demo `clinic_id` | Not run |
| T4.8 | `docker exec clinicready-ai-postgres-1 psql -U clinicready -d clinicready -c "select run_id, seq, tool, result_status, latency_ms from agent_action order by created_at desc limit 20"` | Every tool call of the runs stored | Not run |

There is no audit read endpoint yet (`AUDIT_READ` has none), so audit is checked in SQL.

## 7. Real model

Setup: LOCAL_SETUP.md section 6. Run T2.4 to T2.7, T3.1, T3.2, T3.9 with the key set. Expected risks to watch (found by reading code, **not observed**):

- **Output budget.** The agent's model calls inherit `spring.ai.anthropic.chat.options.max-tokens: 400` from `application.yml` (set for extraction; the agent's `ToolCallingChatOptions` does not override it). A report for 7+ appointments may exceed 400 tokens, be cut off, and end `FAILED` (report not valid JSON). If it happens, it is this setting, not the model.
- **Tool budget.** 15 tool calls per run. A full day (14 appointments) needs 1 list + 14 `checkReadiness` = 15 before any `inspectDocument` or `createFollowUpDraft`, so "prepare tomorrow" for the whole clinic will likely end `TOOL_LIMIT_EXCEEDED`. Use one practitioner (7 appointments).
- **Model.** The agent uses `AI_EXTRACTION_MODEL` (default `claude-haiku-5-5`); there is no separate agent model setting.
- **Time.** 90 s per run; one slow model call can run up to 30 s past that.

## 8. Frontend against the updated backend

| Id | Steps | Expected | Result |
| --- | --- | --- | --- |
| T5.1 | Open http://localhost:3000, log in `reception@nabeul-demo.test` / `password` | Dashboard loads | Not run in a browser; login through nginx proxy PASS (run, API call) |
| T5.2 | Patients, patient detail, documents, appointments (list, detail, admin details, confirmations), Settings → requirement rules (manager) | Pages load and save | Not run in a browser. These endpoints are unchanged on this branch |
| T5.3 | Agent page, Follow-ups page, dashboard KPIs | Placeholders only: no UI calls `/agent-runs`, `/follow-up-tasks` or readiness yet | Confirmed by code reading |
