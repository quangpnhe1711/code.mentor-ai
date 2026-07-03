# Review Execution Engine (pre-AI phase)

This phase runs a QUEUED review job with a **local deterministic analyzer** and persists findings.
**No external AI provider is called.**

## Flow

```
ReviewJob QUEUED
→ RUNNING
→ local deterministic analyzer (over re-materialized sanitized input)
→ review_findings rows
→ COMPLETED
```

On failure after RUNNING:

```
ReviewJob RUNNING
→ FAILED
→ safe error_reason ("Review job failed safely.")
→ review_job_events row
```

Execution is **synchronous** for now (no queue, no async worker). Endpoint:

```
POST /api/organizations/{orgId}/repositories/{repoId}/review-jobs/{reviewJobId}/run
```

Requires platform JWT + organization membership, verifies the repository and job belong to the
org/repo path, and requires the job to be `QUEUED` (else `REVIEW_JOB_NOT_RUNNABLE`).

## Status transitions

Allowed this phase: `QUEUED → RUNNING → COMPLETED` and `QUEUED → RUNNING → FAILED`.
Rejected: any run from `RUNNING`, `COMPLETED`, `FAILED`, or `CANCELED` → `REVIEW_JOB_NOT_RUNNABLE`.

Events are written for `RUNNING`, `COMPLETED`, and `FAILED` (the `QUEUED` event is created at job
creation). Event messages are always safe: no paths, source content, tokens, stack traces, or
provider errors.

## Sanitized input is rebuilt, not stored

The database never stores raw or sanitized source. During execution the sanitized input is **rebuilt
from the snapshot workspace and kept in memory only**, via `ReviewInputMaterializer` (the same
sanitize pipeline used when the analysis input was first built). Findings carry only a file path,
line range, and generic per-rule text — **never a source snippet, secret value, or masked content**.

## Local deterministic analyzer (temporary)

`LocalDeterministicReviewAnalyzer` is a stand-in to validate the pipeline — **not** the final AI
engine. Rules:

| Rule | Severity | Category |
|------|----------|----------|
| `TODO`/`FIXME` comment | LOW | MAINTAINABILITY |
| `console.log` (JS/TS) | LOW | DEBUG_CODE |
| `System.out.println` (Java) | LOW | DEBUG_CODE |
| `printStackTrace` (Java) | MEDIUM | ERROR_HANDLING |
| empty `catch {}` block | MEDIUM | ERROR_HANDLING |
| hardcoded secret-like assignment surviving masking | HIGH | SECURITY |

## Future phase

Async worker / queue and the real external AI provider integration (Claude/etc.), plus PR comments,
webhooks, RAG, and test generation, are future phases and are **not** implemented here.
