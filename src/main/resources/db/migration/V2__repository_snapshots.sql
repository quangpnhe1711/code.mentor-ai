-- Repository clone + snapshot foundation.
-- Source code is NEVER stored in PostgreSQL; only snapshot metadata and a credential-free file
-- inventory live here. Conventions follow V1 (application-generated UUIDs, timestamptz, text+CHECK).

-- repository_snapshots ------------------------------------------------------
create table repository_snapshots (
    id                 uuid        primary key,
    repository_id      uuid        not null,
    organization_id    uuid        not null,
    created_by_user_id uuid        not null,
    status             text        not null,
    source_ref         text,
    commit_sha         text,
    workspace_path     text,
    file_count         integer,
    total_bytes        bigint,
    error_reason       text,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    constraint fk_snapshot_repository foreign key (repository_id) references repositories (id),
    constraint fk_snapshot_organization foreign key (organization_id) references organizations (id),
    constraint fk_snapshot_created_by foreign key (created_by_user_id) references users (id),
    constraint ck_snapshot_status check (status in ('PENDING', 'CLONING', 'SCANNING', 'READY', 'FAILED'))
);
create index idx_repository_snapshots_repository_id on repository_snapshots (repository_id);
create index idx_repository_snapshots_organization_id on repository_snapshots (organization_id);
create index idx_repository_snapshots_status on repository_snapshots (status);

-- repository_file_entries ---------------------------------------------------
create table repository_file_entries (
    id          uuid        primary key,
    snapshot_id uuid        not null,
    path        text        not null,
    language    text,
    size_bytes  bigint      not null,
    included    boolean     not null,
    skip_reason text,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint fk_file_entry_snapshot foreign key (snapshot_id) references repository_snapshots (id),
    constraint uq_file_entry_snapshot_path unique (snapshot_id, path)
);
create index idx_repository_file_entries_snapshot_id on repository_file_entries (snapshot_id);
create index idx_repository_file_entries_included on repository_file_entries (included);
