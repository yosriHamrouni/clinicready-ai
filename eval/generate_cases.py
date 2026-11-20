#!/usr/bin/env python3
"""Generates eval/dataset/eval-cases.json: the 40-case evaluation set (YOS-25).

Deterministic: running it again rewrites the same file. Synthetic data only: no patient names, no real
identifiers. A case describes the situation (rules, documents, open follow-up) and the outcome the
deterministic readiness engine and the agent are expected to reach. EvalDatasetTest checks every
expectation against the real ReadinessEngine, so the ground truth cannot drift from the code.

Run from the repository root:  python eval/generate_cases.py
"""
import json
import pathlib

# Document requirements per appointment type: (category, mandatory, validityDays).
RULES = {
    "CONSULTATION": [("ID_DOCUMENT", True, None), ("INSURANCE_CARD", True, None), ("REFERRAL_LETTER", False, 90)],
    "ULTRASOUND": [("ID_DOCUMENT", True, None), ("PRESCRIPTION", True, 90), ("CONSENT_FORM", False, None)],
    "XRAY": [("ID_DOCUMENT", True, None), ("PRESCRIPTION", True, 90), ("INSURANCE_CARD", False, None)],
    "MRI": [("ID_DOCUMENT", True, None), ("REFERRAL_LETTER", True, 30), ("CONSENT_FORM", True, None),
            ("LAB_RESULT", False, None)],
}
TYPES = list(RULES)
PRACTITIONERS = ["DR_BEN_SALEM", "DR_GHARBI"]  # same two as the demo seed data

SCENARIOS = [
    ("READY_ALL_PRESENT", "All documents present"),
    ("INCOMPLETE_MISSING_MANDATORY", "Missing mandatory document"),
    ("READY_MISSING_OPTIONAL", "Missing optional document"),
    ("REVIEW_UNREADABLE_SCAN", "Unreadable scan"),
    ("INCOMPLETE_EXPIRED", "Expired document"),
    ("REVIEW_CONFLICTING_DATE", "Conflicting date"),
    ("INCOMPLETE_FOLLOW_UP_OPEN", "Follow-up already open"),
    ("REVIEW_EXTRACTION_FAILED", "Extraction failure"),
]
CASES_PER_SCENARIO = 5


def doc(category, **over):
    d = {"category": category, "status": "UPLOADED", "extractionStatus": "EXTRACTED", "reviewReason": None,
         "expiryDaysFromAppointment": None, "uploadedDaysBeforeAppointment": 5, "datesConflict": False}
    d.update(over)
    return d


def build_case(n, scenario_idx, k):
    code, title = SCENARIOS[scenario_idx]
    appt_type = TYPES[(scenario_idx + k) % len(TYPES)]
    rules = RULES[appt_type]
    mandatory = [r for r in rules if r[1]]
    optional = [r for r in rules if not r[1]]
    # The mandatory requirement this case is about; rotates so every requirement gets exercised.
    target = mandatory[k % len(mandatory)]
    docs = {r[0]: doc(r[0]) for r in rules if r[1]}
    if code == "READY_ALL_PRESENT" and optional and k % 2 == 0:
        docs[optional[0][0]] = doc(optional[0][0])  # optional one present too

    # Every requirement is reported: mandatory ones present unless the scenario breaks one; optional ones
    # MISSING unless a document is supplied for them (never changes the aggregate).
    expected_checks = {r[0]: ("PRESENT" if r[1] or r[0] in docs else "MISSING") for r in rules}
    aggregate, tasks, review = "READY", 0, False
    open_follow_up = None
    note = title

    if code == "READY_ALL_PRESENT":
        pass
    elif code == "INCOMPLETE_MISSING_MANDATORY":
        del docs[target[0]]
        expected_checks[target[0]] = "MISSING"
        aggregate, tasks = "INCOMPLETE", 1
    elif code == "READY_MISSING_OPTIONAL":
        pass  # no optional document supplied: already MISSING above
    elif code == "REVIEW_UNREADABLE_SCAN":
        docs[target[0]] = doc(target[0], reviewReason="LOW_CONFIDENCE")
        expected_checks[target[0]] = "NEEDS_REVIEW"
        aggregate, review = "NEEDS_REVIEW", True
    elif code == "INCOMPLETE_EXPIRED":
        validity = target[2]
        if validity is not None and k % 2 == 1:
            docs[target[0]] = doc(target[0], uploadedDaysBeforeAppointment=validity + 10)  # older than validity
        else:
            docs[target[0]] = doc(target[0], expiryDaysFromAppointment=-3)  # expiry date already passed
        expected_checks[target[0]] = "EXPIRED"
        aggregate, tasks = "INCOMPLETE", 1
    elif code == "REVIEW_CONFLICTING_DATE":
        docs[target[0]] = doc(target[0], datesConflict=True)
        expected_checks[target[0]] = "NEEDS_REVIEW"
        aggregate, review = "NEEDS_REVIEW", True
    elif code == "INCOMPLETE_FOLLOW_UP_OPEN":
        del docs[target[0]]
        expected_checks[target[0]] = "MISSING"
        aggregate, tasks = "INCOMPLETE", 1  # the existing task; the agent must not create a second one
        open_follow_up = {"requirementCode": target[0], "status": "AWAITING_APPROVAL"}
    elif code == "REVIEW_EXTRACTION_FAILED":
        docs[target[0]] = doc(target[0], extractionStatus="EXTRACTION_FAILED", reviewReason="EXTRACTION_FAILED")
        expected_checks[target[0]] = "NEEDS_REVIEW"
        aggregate, review = "NEEDS_REVIEW", True

    return {
        "id": f"EV-{n:02d}",
        "scenario": code,
        "title": note,
        "appointmentType": appt_type,
        "practitioner": PRACTITIONERS[n % 2],
        "patientLanguage": "AR" if n % 5 in (0, 2) else "FR",
        "targetRequirement": None if code in ("READY_ALL_PRESENT", "READY_MISSING_OPTIONAL") else target[0],
        "documents": list(docs.values()),
        "openFollowUp": open_follow_up,
        "expected": {
            "aggregate": aggregate,
            "checks": expected_checks,
            "followUpTasks": tasks,
            "duplicateTasks": 0,
            "requiresHumanReview": review,
        },
    }


def main():
    cases = []
    for s in range(len(SCENARIOS)):
        for k in range(CASES_PER_SCENARIO):
            cases.append(build_case(len(cases) + 1, s, k))
    out = {
        "description": "ClinicReady AI evaluation set (YOS-25). Synthetic. Generated by eval/generate_cases.py.",
        "appointmentDay": "2026-11-24",
        "requirementRules": {
            t: [{"category": c, "mandatory": m, "validityDays": v} for c, m, v in rules]
            for t, rules in RULES.items()
        },
        "cases": cases,
    }
    path = pathlib.Path(__file__).parent / "dataset" / "eval-cases.json"
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {len(cases)} cases to {path}")


if __name__ == "__main__":
    main()
