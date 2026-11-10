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

## Out of scope (MVP)

Diagnosis or treatment advice, real patient data, CNAM integration, automatic message sending, WhatsApp/SMS providers, multi-agent setups, billing.
