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

## Metrics

Readiness accuracy · agent report fidelity · extraction accuracy · tool-call success rate · duplicate task rate · approval enforcement · recovery · cost and latency.
