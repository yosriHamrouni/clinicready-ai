# User journey

How clinic staff use ClinicReady AI to get tomorrow's appointments administratively ready. Synthetic data only.
Screens are in French in the app; English names are used here, with the French label where it matters.

## Who

| Role | Goal | Can do |
| --- | --- | --- |
| Receptionist | Every patient arrives tomorrow with the right papers | Read all appointments, upload documents, run the agent, approve follow-ups |
| Practitioner | Know which of *their* patients are not ready | Read own appointments and patients, run the agent on own appointments. Cannot approve follow-ups |
| Clinic manager | Rules are right, staff act, everything is traceable | Everything above, plus requirement rules, reminder templates, full audit log |

Full matrix: [`prd.md`](prd.md#permission-matrix).

## Main journey: the receptionist, the day before

**Moment:** afternoon, about 14 appointments tomorrow. Today this means opening each file and checking papers by hand.

### 1. Sign in, then Dashboard (`/dashboard`)

- Sees tomorrow's appointments with a readiness status: `READY`, `NEEDS_REVIEW`, `INCOMPLETE`.
- Filters by practitioner, status, appointment type.
- Clicks **Prepare appointments** to hand the check to the agent.

### 2. Agent workspace (`/agent`)

- Request box, prefilled or typed: *"Prepare tomorrow's appointments for Dr. Walid Gharbi"*.
- Watches the run live: status (`QUEUED`, `RUNNING`, then `SUCCEEDED`) and a timeline of tool calls (list appointments, check readiness, inspect document, create follow-up draft).
- Reads the report: per appointment, the status and the evidence for each finding (which document, which date, which rule).
- Safety: the report is cross-checked against the stored readiness results. If they disagree, the run ends `REPORT_MISMATCH` and the report is not shown.

### 3. Appointment detail (`/appointments/:id`)

For an appointment that is not `READY`, the checklist shows each requirement:

| Requirement status | What the receptionist does |
| --- | --- |
| `PRESENT` | Nothing |
| `MISSING` / `EXPIRED` (mandatory) | Expect a proposed follow-up (step 4), or upload the document from the patient file (**Ajouter un document**) if the patient already sent it |
| `NEEDS_REVIEW` | Open the document, check or correct the extracted fields (e.g. expiry date), then **Vérifier** (readiness trusts it) or **Rejeter** with a reason (counts as missing). Never turned into a reminder automatically |
| `NOT_APPLICABLE` | Nothing |

Optional requirements stay visible but never block `READY`.

### 4. Follow-up center (`/follow-ups`)

- One task per missing or expired mandatory document, each with a message draft in French or Arabic from the clinic's reminder template.
- The receptionist reviews the draft, edits it (**Enregistrer le texte**), then clicks **Approuver** or **Rejeter** (reject needs a comment).
- Nothing is sent automatically. After approval, staff send the message themselves (phone, WhatsApp, SMS) and click **Marquer comme envoyée**.
- When the patient brings the document: upload it on the patient file (**Ajouter un document**), check it (**Vérifier**), then click **Marquer comme résolue**.

Task lifecycle: `PROPOSED` to `AWAITING_APPROVAL` to `APPROVED` or `REJECTED`; `APPROVED` to `DELIVERED` to `RESOLVED`. No other moves.

Re-running the agent the same day creates no duplicate task for the same appointment and requirement.

### 5. Next morning

- Dashboard again: resolved items are now `READY`.
- Anything still `INCOMPLETE` is known before the patient arrives, not at the desk.

**Outcome:** a 14-file manual check becomes one request, a short review, and a few approved messages.

## Practitioner journey

1. Signs in, sees only own appointments on the dashboard.
2. Runs *"Prepare my appointments for tomorrow"*. The agent only touches their appointments.
3. Reads the report and appointment checklists. Can correct administrative fields on documents, but cannot upload, verify or reject them.
4. Sees proposed follow-ups but cannot approve them: the front desk does.

## Clinic manager journey

1. **Configure** (`/settings/requirements`): appointment types and their requirement rules (mandatory or optional, validity such as "insurance card not expired on appointment date").
2. **Templates:** reminder wording in French and Arabic.
3. **Supervise** (`/audit`): every agent run, tool call, approval, rejection and delivery, with who and when.
4. **Check quality:** readiness stats and the evaluation report ([`../eval/report.md`](../eval/report.md)).

## Where the journey stops (MVP)

- No automatic sending: staff deliver every message themselves.
- No CNAM integration, no medical advice: `READY` means administratively ready only.
- With no model configured, every agent run ends `FAILED` / `No model is configured`. Dashboard, checklists and follow-ups still work.

## Not yet validated

The journey comes from the PRD and the built screens, not from receptionist interviews (YOS-5, YOS-7 still in backlog).
