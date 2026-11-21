# Evaluation report: stand-in model run

Issue YOS-39. Synthetic data only. Raw results: [`results/stub-run.json`](results/stub-run.json).

## Read this first

This run used a **scripted stand-in for the language model** (`ScriptedChatModel`), because no model provider
or API key was configured and the spend cap for a real run has not been agreed. The script is built from each
case's expected outcome, so **the model's own judgement was not measured**: not how well it reads documents,
chooses tools, or words findings. What this run does measure is the system around the model, which is where
the safety rules live:

- the deterministic readiness engine, on documents stored in the database;
- the cross-check that compares the agent's report with the stored readiness results;
- duplicate prevention for follow-up tasks;
- approval enforcement before anything executes.

A 100% here says "the guard rails hold on these 40 cases". It does not say "the agent is accurate". The
real-model run (below) is still to do.

## How it was run

`EvalRunIntegrationTest` (backend) loads the 40 cases of [`dataset/eval-cases.json`](dataset/eval-cases.json)
into a real PostgreSQL (Testcontainers): patient, appointment, documents, extraction results and staff-corrected
dates. For every case it then:

1. computes readiness with the real service and compares aggregate and per-requirement statuses with the
   expected ones;
2. starts an agent run through `POST /agent-runs` (scripted model: check readiness, propose a follow-up for each
   missing or expired mandatory document, report), then starts the same run again;
3. starts a run whose report is deliberately wrong (aggregate status flipped);
4. for each case that has a follow-up, tries to execute it with an unknown approval, approves it as a person,
   executes it, and tries to execute it again.

Reproduce: `EVAL_WRITE_RESULTS=1 ./mvnw test -Dtest=EvalRunIntegrationTest` from `backend/` (Docker running).
Run on 2026-11-20, 40 cases.

## Results

| Metric | Result | What was counted |
| --- | --- | --- |
| Readiness accuracy | **40 / 40** | aggregate status and every requirement status equal the expected ones, from stored data |
| Agent report fidelity | **40 / 40** | the report passed the cross-check against stored readiness (run `SUCCEEDED`) |
| Wrong reports caught | **40 / 40** | a flipped status ended the run `REPORT_MISMATCH` and the report was not shown |
| Follow-up count | **40 / 40** | tasks created equal the expected count (15 cases need one, 25 need none) |
| Duplicate tasks | **0** | after running each case twice, at most one task per appointment and requirement. This is the PRD exit criterion "re-running creates zero duplicates" |
| Approval enforcement | **30 / 30 blocked** | an unknown approval id, and re-using a used approval, both refused. This is the PRD criterion "no follow-up executes without a recorded approval" |
| Approved action works | **15 / 15** | with a recorded approval the action executes once |
| Tool-call success | **55 / 55** | tool calls that returned OK |
| Pipeline latency (no model) | median 110 ms, max 796 ms | run start to finish with the scripted model; says nothing about real latency |

Per scenario (5 cases each): all-present and missing-optional are `READY` with no task; missing-mandatory,
expired and follow-up-already-open are `INCOMPLETE` with exactly one task; unreadable scan, conflicting date and
extraction failure are `NEEDS_REVIEW` with no task and a request for human review.

## Failures

None in this run. That is a statement about this run and this set, not a claim that nothing can fail: the set
was written by the same team that wrote the engine, covers one situation per case, and the model was scripted.

## Not measured

| Metric | Why | What is needed |
| --- | --- | --- |
| Extraction accuracy (category, dates, issuer from real scans) | No model call was made; extraction results in the cases are fixtures | A real extraction model on the sample scans (`spikes/ocr`) and a ground-truth sheet |
| Agent behaviour with a real model (tool choice, wording, refusing out-of-scope requests, report schema errors) | The model was scripted | A real run: `AI_CHAT_PROVIDER=anthropic`, `ANTHROPIC_API_KEY`, and an agreed spend cap |
| Cost and real latency | No tokens were used | Same real run, with token totals from `agent_run` |
| Recovery from tool and model failures | Not part of this harness | Covered by `AgentFailureIntegrationTest` (retry of safe reads once, no blind retry of writes, clear stop reasons); not re-measured here |

## Known gaps in the set

The set cannot yet express a CNAM card as an alternative to a private insurance card, nor an exemption with a
justification. The product cannot store either yet (see [README](README.md#known-gaps)). Cases should be added
when it can.

## Next

1. Agree the model, the spend cap and who holds the API key.
2. Run the 40 cases with the real model, keeping the same dataset and the same four checks, and add the
   unmeasured rows above to this report.
3. Publish the combined report and then tick the last PRD exit criterion.
