# Agent design and safety boundaries

## Where agentic decisions happen

| Decision | Who makes it | Where |
| --- | --- | --- |
| Which appointments to look at, which tools to call, in what order | the model | `AgentOrchestrator` loop |
| Whether a document needs a closer look (`inspectDocument`) | the model | tool choice |
| Which gaps deserve a follow-up proposal | the model, within the rules of the system prompt | `createFollowUpDraft` call |
| The wording of the summary | the model | final report |
| Whether a requirement is met; the aggregate status | **`ReadinessEngine`** (plain Java) | `readiness` package |
| Whether the report is acceptable | **the orchestrator** (schema + cross-check) | `AgentOrchestrator.report` |
| Whether a follow-up may go out | **a person** with `FOLLOWUP_APPROVE` | `POST /follow-up-tasks/{id}/approve` |
| Whether a follow-up was delivered or resolved | **a person**, or the agent with that person's approval id | `FollowUpService` |

The model never decides a status: it reads one. A report that contradicts the engine fails the run (`REPORT_MISMATCH`).

## Why the agent cannot act without approval

1. **No tool for it.** The eight tools are listed below. None approves, rejects, resolves or marks delivered. The approval endpoints live in `FollowUpController`, a separate API the agent loop never calls; a test asserts no tool name contains approve, reject, deliver or resolve (other than `submitApprovedAction`).
2. **Unknown tools do nothing.** If the model names a tool that is not registered (say `approveFollowUp`), the call is recorded as `UNKNOWN_TOOL` and returns an error to the model.
3. **The one write that executes needs a human's decision id.** `submitApprovedAction(taskId, approvalId)` succeeds only if `approvalId` is that task's recorded `APPROVED` decision and the task is still `APPROVED`. The decision row is created only by `approve`, which requires `FOLLOWUP_APPROVE` (receptionist or manager). An approval is used once.
4. **The database agrees.** A check constraint forbids any task status past `AWAITING_APPROVAL` without an `approval_id`; illegal transitions are refused by the service (`409 INVALID_TRANSITION`) and again by the entity.
5. **The agent acts as its initiator.** The worker thread carries the initiator's identity, so a practitioner's run cannot see another practitioner's appointments, and nobody's run can see another clinic.
6. **Tested on every path.** API, service and tool attempts without approval all fail (`MvpAcceptanceIntegrationTest`, `FollowUpApiIntegrationTest`).

## Tool contracts

All tools take string arguments (a bad id or date is `INVALID_INPUT`, not an exception), run as the initiator, call domain services only, and return `ToolResult { status, message, data }` with `status` one of `OK`, `NOT_FOUND`, `FORBIDDEN`, `INVALID_INPUT`, `CONFLICT`, `ERROR`.

| Tool | Input | Output (`data`) | Kind |
| --- | --- | --- | --- |
| `getUpcomingAppointments` | `practitionerId?`, `dateFrom`, `dateTo` (clinic dates, ≤ 92 days) | `[{appointmentId, patientId, patientRef, appointmentTypeId, appointmentType, practitionerId, startsAt}]` | read |
| `getAppointmentRequirements` | `appointmentTypeId` | `[{requirementCode, category, mandatory, validityDays, needsVerification}]` incl. `EXPECTED_EXAMINATIONS`, `ADVANCE_PAYMENT` | read |
| `listPatientDocuments` | `patientId` | `[{documentId, category, version, docDate, extractionStatus, confidence, needsReview}]` | read |
| `inspectDocument` | `documentId`, `fields[]` ⊆ category, documentDate, expiryDate, issuer | `{documentId, declaredCategory, extractionStatus, fields[{field, value, source, confidence}], evidence{documentId, version, model, observedAt}}`; `ERROR` when the text or AI extraction failed | read |
| `checkReadiness` | `appointmentId` | `{appointmentId, status, checks[{requirementCode, mandatory, status, evidenceDocumentIds, reason, requiresHumanReview}]}` | read (recalculates, idempotent) |
| `getFollowUpStatus` | `taskId` or `appointmentId` | `{tasks[{taskId, appointmentId, requirementCode, status, createdAt}]}` | read |
| `createFollowUpDraft` | `appointmentId`, `requirementCode`, `language?` | `{taskId, status: AWAITING_APPROVAL, outcome: CREATED or EXISTING}` | draft write, idempotent |
| `submitApprovedAction` | `taskId`, `approvalId` | `{taskId, status: DELIVERED, outcome: EXECUTED}`; `CONFLICT` without a valid approval | controlled write |

Outputs never contain patient names, phone numbers, file names or document text.

## The bounded loop

- At most **15 tool calls** and **90 s** per run (`clinicready.ai.max-tool-calls`, `clinicready.ai.run-timeout`), checked before every model call and every tool call. Exceeding either ends the run `TOOL_LIMIT_EXCEEDED` or `TIMED_OUT`.
- **Retries:** a read that throws or returns `ERROR` is tried once more; a write is never retried. A tool that still throws stops the run (`FAILED`, tool named). A model call that fails ends the run `FAILED` with the exception type.
- **Final answer:** JSON `{summary, appointments[{appointmentId, status, findings[{requirementCode, status, action}]}]}`. Invalid JSON fails the run. Counts are computed by the server. The report fails as `REPORT_MISMATCH` if an aggregate or finding disagrees with stored readiness, a blocking mandatory check is not listed, or an appointment the run checked is left out.
- Every tool call is an `AgentAction`: tool, redacted arguments, result status, latency, tokens of the model turn.

## Example execution traces

Illustrative runs in the exact shapes `GET /agent-runs/{id}` returns; ids are shortened. Each step is exercised by the scripted-model integration tests (`PrepareTomorrowEndToEndTest`, `AgentRunIntegrationTest`, `FollowUpApiIntegrationTest`); they were not recorded against the real provider.

### 1. "Prepare tomorrow's appointments for Dr. X" (succeeds)

| # | Tool | Arguments | Result |
| --- | --- | --- | --- |
| 1 | `getUpcomingAppointments` | `{practitionerId: "9a2d…", dateFrom: "2026-11-18", dateTo: "2026-11-18"}` | `OK`: 2 appointments, `P-0007` and `P-0019` |
| 2 | `checkReadiness` | `{appointmentId: "c0de…"}` | `OK`: `INCOMPLETE`; CONSENT_FORM `MISSING`, EXPECTED_EXAMINATIONS `NEEDS_REVIEW`, ADVANCE_PAYMENT `NEEDS_REVIEW` |
| 3 | `checkReadiness` | `{appointmentId: "f00d…"}` | `OK`: `NEEDS_REVIEW`; EXPECTED_EXAMINATIONS and ADVANCE_PAYMENT `NEEDS_REVIEW` |
| 4 | `createFollowUpDraft` | `{appointmentId: "c0de…", requirementCode: "CONSENT_FORM"}` | `OK`: task `7a11…`, `AWAITING_APPROVAL`, `CREATED` |

Final report (validated, cross-checked): `counts {ready: 0, needsReview: 1, incomplete: 1}`, status `SUCCEEDED`. Then a receptionist approves task `7a11…` and marks it delivered: readiness is re-checked (CONSENT_FORM still `MISSING` until the form arrives), and the audit trail reads `FOLLOW_UP_TASK_CREATED` (with the run id), `_APPROVED`, `_DELIVERED`.

### 2. The model tries to execute without approval (contained)

| # | Tool | Arguments | Result |
| --- | --- | --- | --- |
| 1 | `checkReadiness` | `{appointmentId: "c0de…"}` | `OK`: `INCOMPLETE` |
| 2 | `createFollowUpDraft` | `{appointmentId: "c0de…", requirementCode: "CONSENT_FORM"}` | `OK`: task `7a11…`, `EXISTING` (opened by an earlier run) |
| 3 | `approveFollowUp` | `{taskId: "7a11…"}` | `UNKNOWN_TOOL`: no such tool |
| 4 | `submitApprovedAction` | `{taskId: "7a11…", approvalId: "1234…"}` | `CONFLICT`: no valid approval of this task with that id |

The task stays `AWAITING_APPROVAL`. The run can still succeed with a correct report: the agent has proposed, and only a person can approve.
