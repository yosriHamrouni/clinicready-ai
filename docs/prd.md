# Product requirements

Full PRD: https://claude.ai/artifact/755hmHJBAtN9Eq8pVupkug

## Summary

ClinicReady AI tells clinic staff, the day before, which appointments are administratively ready and what to do about the ones that aren't.

## MVP exit criteria

- [ ] A receptionist can run "Prepare tomorrow's appointments for Dr. X" and get a readiness report with evidence per finding.
- [ ] Missing mandatory documents produce proposed follow-ups with FR/AR draft reminders.
- [ ] No follow-up executes without a recorded approval (proved by tests).
- [ ] Re-running the same request creates zero duplicate tasks.
- [ ] Agent run, tool calls and approvals are visible in the UI and the audit log.
- [ ] Evaluation report published with measured results, failures included.

## Readiness statuses

| Status | Meaning |
| --- | --- |
| `PRESENT` | A matching document has been identified. |
| `MISSING` | No matching document found in the sources checked. |
| `NEEDS_REVIEW` | The system cannot safely establish whether the requirement is met. |
| `EXPIRED` | A configured validity rule is not satisfied. |
| `NOT_APPLICABLE` | The requirement does not apply to this appointment. |

## Permission matrix

From the full PRD ("Users and permissions"). Every user is scoped to one clinic: a record of another clinic is reported as `404 NOT_FOUND`. A forbidden action returns `403 FORBIDDEN`. Enforced in services by `AuthorizationService` (`backend/.../security/Permission.java`), not only in controllers.

| Permission | Receptionist | Practitioner | Clinic manager |
| --- | --- | --- | --- |
| `APPOINTMENT_READ` | All in clinic | Own only | All in clinic |
| `PATIENT_READ` | All in clinic | Patients they have an appointment with | All in clinic |
| `DOCUMENT_READ` | Yes | Own patients' | Yes |
| `DOCUMENT_UPLOAD` | Yes | No | Yes |
| `DOCUMENT_METADATA_EDIT` | Yes | Administrative fields only | Yes |
| `AGENT_RUN` | Yes | Own appointments only | Yes |
| `FOLLOWUP_APPROVE` (approve / reject / edit) | Yes | No | Yes |
| `REQUIREMENT_RULES_MANAGE` (appointment types and rules) | No | No | Yes |
| `USER_MANAGE` (users, roles, practitioners) | No | No | Yes |
| `TEMPLATE_MANAGE` (reminder templates, approval policy) | No | No | Yes |
| `AUDIT_READ` (audit log, readiness stats) | Own actions | No | All |
| `PATIENT_WRITE`, `APPOINTMENT_WRITE` | Yes | No | Yes |

The last row is not in the full PRD: front-desk data entry is given to receptionists and managers. Rows with a narrower scope ("own …") are applied by the owning service on top of the permission.

## Out of scope (MVP)

Diagnosis or treatment advice, real patient data, CNAM integration, automatic message sending, WhatsApp/SMS providers, multi-agent setups, billing.
