-- Retry/backoff metadata for review worker execution.
-- Values are safe operational metadata only; failure reason remains generic and never stores provider
-- payloads, local paths, source content, credentials, or stack traces.

alter table review_jobs
    add column attempt_count integer not null default 0,
    add column max_attempts integer not null default 3,
    add column next_run_at timestamptz,
    add column last_failure_reason text;

alter table review_jobs
    add constraint ck_review_job_attempts
        check (attempt_count >= 0 and max_attempts > 0 and attempt_count <= max_attempts);

create index idx_review_jobs_ready_queue on review_jobs (status, next_run_at, created_at)
    where status = 'QUEUED';
