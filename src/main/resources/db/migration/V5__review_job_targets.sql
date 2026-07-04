-- Review target metadata for manual review (FR-006).
-- These fields are safe provider metadata only. They never store source code, diffs, comments,
-- credentials, provider error payloads, or workspace paths.

alter table review_jobs
    add column target_pull_request_number integer,
    add column target_ref text;

alter table review_jobs
    add constraint ck_review_job_review_type
        check (review_type in ('FULL_REPOSITORY', 'PULL_REQUEST', 'BRANCH')),
    add constraint ck_review_job_target_shape
        check (
            (review_type = 'FULL_REPOSITORY'
                and target_pull_request_number is null
                and target_ref is null)
            or
            (review_type = 'PULL_REQUEST'
                and target_pull_request_number is not null
                and target_pull_request_number > 0
                and target_ref is null)
            or
            (review_type = 'BRANCH'
                and target_pull_request_number is null
                and target_ref is not null
                and length(btrim(target_ref)) > 0)
        );

create index idx_review_jobs_target_pr on review_jobs (repository_id, target_pull_request_number)
    where target_pull_request_number is not null;
create index idx_review_jobs_target_ref on review_jobs (repository_id, target_ref)
    where target_ref is not null;
