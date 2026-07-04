-- Test Generation foundation.
-- Generated tests are stored as suggestions and are never written to the repository automatically.

create table test_generation_jobs (
    id                         uuid        primary key,
    organization_id            uuid        not null,
    repository_id              uuid        not null,
    snapshot_id                uuid        not null,
    analysis_input_id          uuid        not null,
    created_by_user_id         uuid        not null,
    status                     text        not null,
    target_type                text        not null,
    target_file_path           text,
    target_pull_request_number integer,
    prompt_version             text        not null,
    ai_provider                text        not null,
    ai_model                   text        not null,
    input_hash                 text,
    generated_test_count       integer     not null,
    error_reason               text,
    created_at                 timestamptz not null,
    updated_at                 timestamptz not null,
    constraint fk_test_job_organization foreign key (organization_id) references organizations (id),
    constraint fk_test_job_repository foreign key (repository_id) references repositories (id),
    constraint fk_test_job_snapshot foreign key (snapshot_id) references repository_snapshots (id),
    constraint fk_test_job_analysis_input foreign key (analysis_input_id) references code_analysis_inputs (id),
    constraint fk_test_job_created_by foreign key (created_by_user_id) references users (id),
    constraint ck_test_job_status check (status in ('GENERATING', 'COMPLETED', 'FAILED')),
    constraint ck_test_job_target_type check (target_type in ('FILE', 'PULL_REQUEST')),
    constraint ck_test_job_target_shape check (
        (target_type = 'FILE' and target_file_path is not null and target_pull_request_number is null)
        or (target_type = 'PULL_REQUEST' and target_file_path is null and target_pull_request_number is not null and target_pull_request_number > 0)
    )
);
create index idx_test_generation_jobs_repo on test_generation_jobs (organization_id, repository_id);
create index idx_test_generation_jobs_analysis_input on test_generation_jobs (analysis_input_id);

create table generated_tests (
    id                     uuid        primary key,
    test_generation_job_id uuid        not null,
    organization_id        uuid        not null,
    repository_id          uuid        not null,
    file_path              text        not null,
    language               text        not null,
    content                text        not null,
    rationale              text        not null,
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    constraint fk_generated_test_job foreign key (test_generation_job_id) references test_generation_jobs (id),
    constraint fk_generated_test_organization foreign key (organization_id) references organizations (id),
    constraint fk_generated_test_repository foreign key (repository_id) references repositories (id)
);
create index idx_generated_tests_job on generated_tests (test_generation_job_id);

create table test_runs (
    id                uuid        primary key,
    generated_test_id uuid        not null,
    organization_id   uuid        not null,
    repository_id     uuid        not null,
    status            text        not null,
    sandbox_mode      text        not null,
    timeout_seconds   integer     not null,
    started_at        timestamptz,
    completed_at      timestamptz,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    constraint fk_test_run_generated_test foreign key (generated_test_id) references generated_tests (id),
    constraint fk_test_run_organization foreign key (organization_id) references organizations (id),
    constraint fk_test_run_repository foreign key (repository_id) references repositories (id),
    constraint ck_test_run_status check (status in ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'TIMEOUT', 'ERROR'))
);
create index idx_test_runs_generated_test on test_runs (generated_test_id);

create table test_results (
    id                  uuid        primary key,
    test_run_id         uuid        not null,
    stdout              text        not null,
    stderr              text        not null,
    exit_code           integer,
    duration_ms         bigint      not null,
    failure_explanation text,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_test_result_run foreign key (test_run_id) references test_runs (id),
    constraint uq_test_result_run unique (test_run_id)
);
