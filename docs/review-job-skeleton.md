# Review Job Skeleton (pre-AI phase)

This phase adds review-job persistence and a read/create API. **No AI provider is called.**

## Pipeline

```
RepositorySnapshot READY
→ CodeAnalysisInput READY
→ ReviewJob QUEUED
→ future worker/AI phase (processes the job, generates findings)
```

Creating a review job only validates the ownership chain and input readiness, then persists a
`QUEUED` job plus its first `QUEUED` event.

## Scope of this phase

- **Does not call AI.** No Spring AI, no Claude/OpenAI/Gemini, no worker, no queue, no webhook, no RAG.
- The `review_findings` table exists for schema stability, but **no findings are generated yet** — the
  findings endpoint always returns an empty list.
- `input_hash` is **copied verbatim** from `code_analysis_inputs.input_hash` into `review_jobs.input_hash`
  for traceability: a job records exactly which sanitized input it was created from.
- **Sanitized source is not stored** in `review_jobs` or `review_findings` (nor anywhere else in the DB).
  These tables hold only ids, lifecycle state, counts, and safe metadata.

## Endpoints

All require a platform JWT and organization membership, and enforce the chain
`organization → repository → snapshot → code_analysis_input → review_job`.

```
POST /api/organizations/{orgId}/repositories/{repoId}/analysis-inputs/{analysisInputId}/review-jobs
GET  /api/organizations/{orgId}/repositories/{repoId}/review-jobs
GET  /api/organizations/{orgId}/repositories/{repoId}/review-jobs/{reviewJobId}
GET  /api/organizations/{orgId}/repositories/{repoId}/review-jobs/{reviewJobId}/findings
GET  /api/organizations/{orgId}/repositories/{repoId}/review-jobs/{reviewJobId}/events
```

Only `reviewType = FULL_REPOSITORY` is supported; unknown values are rejected with `VALIDATION_FAILED`.
A non-READY analysis input is rejected with `CODE_ANALYSIS_INPUT_NOT_READY`.

## TBDs intentionally preserved

- Job lifecycle mutators (RUNNING/COMPLETED/FAILED/CANCELED) — added when the worker phase lands.
- `ai_provider` / `ai_model` / `prompt_version` / `started_at` / `completed_at` — populated by the worker.
- `ReviewFinding` has no public constructor yet — added when findings are generated.
