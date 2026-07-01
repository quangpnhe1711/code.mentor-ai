-- Sanitized AI-review input preparation (pre-AI phase).
-- This table stores ONLY metadata about a sanitized review input built from a READY snapshot:
-- counts, a stable content hash, and a safe error reason. It NEVER stores source code, masked or
-- unmasked file content, secrets, workspace paths, or credentials. Conventions follow V1/V2
-- (application-generated UUIDs, timestamptz, text + CHECK).

create table code_analysis_inputs (
    id                  uuid        primary key,
    snapshot_id         uuid        not null,
    repository_id       uuid        not null,
    organization_id     uuid        not null,
    created_by_user_id  uuid        not null,
    status              text        not null,
    input_hash          text,
    included_file_count integer     not null default 0,
    skipped_file_count  integer     not null default 0,
    masked_secret_count integer     not null default 0,
    total_input_bytes   bigint      not null default 0,
    error_reason        text,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_analysis_input_snapshot foreign key (snapshot_id) references repository_snapshots (id),
    constraint fk_analysis_input_repository foreign key (repository_id) references repositories (id),
    constraint fk_analysis_input_organization foreign key (organization_id) references organizations (id),
    constraint fk_analysis_input_created_by foreign key (created_by_user_id) references users (id),
    constraint ck_analysis_input_status check (status in ('BUILDING', 'READY', 'FAILED'))
);
create index idx_code_analysis_inputs_snapshot_id on code_analysis_inputs (snapshot_id);
create index idx_code_analysis_inputs_repository_id on code_analysis_inputs (repository_id);
create index idx_code_analysis_inputs_organization_id on code_analysis_inputs (organization_id);
create index idx_code_analysis_inputs_status on code_analysis_inputs (status);
