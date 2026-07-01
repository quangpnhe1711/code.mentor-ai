-- Review job persistence (pre-AI phase; no provider is called yet).
-- A review_jobs row represents a FUTURE AI-review execution over a READY code_analysis_inputs row.
-- These tables store ONLY metadata: ids, lifecycle state, a copied input_hash (traceability), and
-- safe fields. They NEVER store source code (masked or not), workspace paths, or credentials.
-- Findings exist as a table but are NOT generated in this phase. Conventions follow V1/V2/V3
-- (application-generated UUIDs, timestamptz, text + CHECK — no native enum types).

create table review_jobs (
    id                  uuid        primary key,
    organization_id     uuid        not null,
    repository_id       uuid        not null,
    snapshot_id         uuid        not null,
    analysis_input_id   uuid        not null,
    created_by_user_id  uuid        not null,
    status              text        not null,
    review_type         text        not null default 'FULL_REPOSITORY',
    input_hash          text        not null,
    ai_provider         text,
    ai_model            text,
    prompt_version      text,
    total_findings      integer     not null default 0,
    error_reason        text,
    started_at          timestamptz,
    completed_at        timestamptz,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_review_job_organization foreign key (organization_id) references organizations (id),
    constraint fk_review_job_repository foreign key (repository_id) references repositories (id),
    constraint fk_review_job_snapshot foreign key (snapshot_id) references repository_snapshots (id),
    constraint fk_review_job_analysis_input foreign key (analysis_input_id) references code_analysis_inputs (id),
    constraint fk_review_job_created_by foreign key (created_by_user_id) references users (id),
    constraint ck_review_job_status check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELED'))
);
create index idx_review_jobs_organization_id on review_jobs (organization_id);
create index idx_review_jobs_repository_id on review_jobs (repository_id);
create index idx_review_jobs_snapshot_id on review_jobs (snapshot_id);
create index idx_review_jobs_analysis_input_id on review_jobs (analysis_input_id);
create index idx_review_jobs_status on review_jobs (status);
create index idx_review_jobs_created_by_user_id on review_jobs (created_by_user_id);

create table review_findings (
    id              uuid          primary key,
    review_job_id   uuid          not null,
    organization_id uuid          not null,
    repository_id   uuid          not null,
    file_path       text          not null,
    line_start      integer,
    line_end        integer,
    severity        text          not null,
    category        text          not null,
    title           text          not null,
    description     text          not null,
    suggestion      text,
    rule_id         text,
    confidence      numeric(5, 4),
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    constraint fk_review_finding_job foreign key (review_job_id) references review_jobs (id),
    constraint fk_review_finding_organization foreign key (organization_id) references organizations (id),
    constraint fk_review_finding_repository foreign key (repository_id) references repositories (id),
    constraint ck_review_finding_severity check (severity in ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);
create index idx_review_findings_review_job_id on review_findings (review_job_id);
create index idx_review_findings_organization_id on review_findings (organization_id);
create index idx_review_findings_repository_id on review_findings (repository_id);
create index idx_review_findings_severity on review_findings (severity);
create index idx_review_findings_category on review_findings (category);

-- Append-only status-transition log; intentionally has no updated_at.
create table review_job_events (
    id            uuid        primary key,
    review_job_id uuid        not null,
    status        text        not null,
    message       text,
    created_at    timestamptz not null,
    constraint fk_review_job_event_job foreign key (review_job_id) references review_jobs (id)
);
create index idx_review_job_events_review_job_id on review_job_events (review_job_id);
