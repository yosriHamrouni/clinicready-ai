# Evaluation

Built in YOS-25 (test set) and YOS-39 (report).

## Test set

40 synthetic appointments across 4 appointment types and 2 practitioners. Each case appears at least 3 times:

1. All documents present → `READY`, no task
2. Missing mandatory document → `INCOMPLETE`, one task
3. Missing optional document → `READY`, no task
4. Unreadable scan → `NEEDS_REVIEW`
5. Expired document → `EXPIRED`, one task
6. Conflicting date → `NEEDS_REVIEW`
7. Follow-up already open → existing task returned, no duplicate
8. Extraction failure → `NEEDS_REVIEW` with reason

### Dataset files

- [`dataset/eval-cases.json`](dataset/eval-cases.json): the 40 cases (EV-01 to EV-40), 5 per scenario, spread
  over CONSULTATION, ULTRASOUND, XRAY and MRI (10 each), the two demo practitioners (20 each) and French or
  Arabic patients (24 / 16). Synthetic: no names or identifiers.
- [`generate_cases.py`](generate_cases.py): writes that file. Edit the script, not the JSON, then run
  `python eval/generate_cases.py` from the repository root.

Each case holds the requirement rules of its type, the patient's documents (category, staff status,
extraction status and review reason, expiry, age, conflicting dates), an optional already-open follow-up, and
the expected outcome: aggregate status, status of every requirement, number of follow-up tasks (a
duplicate is a second task for the same requirement, expected 0) and whether human review is required.
Offsets are relative to the appointment day, so the set does not age.

`EvalDatasetTest` (backend) runs every case through the real `ReadinessEngine` and fails if an expectation
disagrees, and checks the shape promised above. The agent-level expectations (task counts, no duplicate in
the "follow-up already open" cases) are measured in the evaluation run (YOS-39).

### Known gaps

Two situations from the PRD are not in the set because the product cannot represent them yet. Cases for them
should be added when the features exist:

- **CNAM card or private insurance card (alternatives).** The engine accepts several categories for one
  requirement, but there is no `CNAM_CARD` category and a stored rule names one category. Today the set only
  uses INSURANCE_CARD.
- **Exemption with justification.** The engine returns `NOT_APPLICABLE` for an exempted requirement, but
  nothing stores an exemption yet.

## Metrics

Readiness accuracy · agent report fidelity · extraction accuracy · tool-call success rate · duplicate task rate · approval enforcement · recovery · cost and latency.
