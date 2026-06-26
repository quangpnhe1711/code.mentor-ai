-- Foundation schema for CodeMentor AI.
-- Source of truth: docs/14-foundation-persistence-domain-design.md (§4) and docs/03-domain-model.md.
-- Conventions (ADR-003 / doc 14 §4.1):
--   * UUID primary keys (application-generated; no DB defaults).
--   * timestamptz for all timestamps.
--   * Status/type/role stored as text + CHECK constraints (NOT native PostgreSQL ENUM types).

-- 3.1 / 4.2 users -----------------------------------------------------------
create table users (
    id             uuid        primary key,
    github_user_id text        not null,
    github_login   text        not null,
    email          text,
    display_name   text,
    avatar_url     text,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    deleted_at     timestamptz,
    constraint uq_users_github_user_id unique (github_user_id)
);
create index idx_users_github_login on users (github_login);

-- 3.2 / 4.3 organizations ---------------------------------------------------
create table organizations (
    id            uuid        primary key,
    name          text        not null,
    slug          text        not null,
    type          text        not null,
    owner_user_id uuid        not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    deleted_at    timestamptz,
    constraint uq_organizations_slug unique (slug),
    constraint fk_org_owner_user foreign key (owner_user_id) references users (id),
    constraint ck_org_type check (type in ('PERSONAL', 'TEAM'))
);
-- ADR-010 / doc 14 §3.2: exactly one PERSONAL organization per owner user.
create unique index uq_personal_org_per_owner on organizations (owner_user_id) where type = 'PERSONAL';
create index idx_org_owner_user on organizations (owner_user_id);

-- 3.3 / 4.4 organization_members -------------------------------------------
create table organization_members (
    id              uuid        primary key,
    organization_id uuid        not null,
    user_id         uuid        not null,
    role            text        not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint uq_org_member unique (organization_id, user_id),
    constraint fk_member_org foreign key (organization_id) references organizations (id),
    constraint fk_member_user foreign key (user_id) references users (id),
    constraint ck_member_role check (role in ('OWNER', 'ADMIN', 'REVIEWER', 'DEVELOPER', 'VIEWER'))
);
create index idx_member_user on organization_members (user_id);
create index idx_member_org on organization_members (organization_id);

-- 3.5 / 4.6 git_provider_connections ---------------------------------------
-- ADR-011: provider tokens are stored encrypted at rest (bytea ciphertext only).
create table git_provider_connections (
    id                      uuid        primary key,
    provider                text        not null,
    user_id                 uuid        not null,
    provider_account_login  text        not null,
    provider_account_id     text        not null,
    encrypted_access_token  bytea       not null,
    encrypted_refresh_token bytea,
    token_expires_at        timestamptz,
    scopes                  text,
    key_version             text        not null,
    encryption_algorithm    text        not null,
    status                  text        not null,
    created_at              timestamptz not null,
    updated_at              timestamptz not null,
    constraint uq_provider_connection unique (provider, provider_account_id, user_id),
    constraint fk_conn_user foreign key (user_id) references users (id),
    constraint ck_conn_provider check (provider in ('GITHUB')),
    constraint ck_conn_status check (status in ('CONNECTED', 'DISCONNECTED', 'EXPIRED'))
);
create index idx_conn_user on git_provider_connections (user_id);
create index idx_conn_expiry on git_provider_connections (token_expires_at);

-- 3.4 / 4.5 repositories ----------------------------------------------------
create table repositories (
    id                         uuid        primary key,
    organization_id            uuid        not null,
    git_provider_connection_id uuid        not null,
    provider                   text        not null,
    external_repo_id           text        not null,
    owner_login                text        not null,
    name                       text        not null,
    full_name                  text        not null,
    visibility                 text        not null,
    default_branch             text,
    status                     text        not null,
    imported_by_user_id        uuid        not null,
    last_synced_at             timestamptz,
    error_reason               text,
    created_at                 timestamptz not null,
    updated_at                 timestamptz not null,
    deleted_at                 timestamptz,
    -- BR-REP-002: a repository is imported at most once per organization.
    constraint uq_repo_per_org unique (organization_id, provider, external_repo_id),
    constraint fk_repo_org foreign key (organization_id) references organizations (id),
    constraint fk_repo_connection foreign key (git_provider_connection_id) references git_provider_connections (id),
    constraint fk_repo_imported_by foreign key (imported_by_user_id) references users (id),
    constraint ck_repo_provider check (provider in ('GITHUB')),
    constraint ck_repo_visibility check (visibility in ('PUBLIC', 'PRIVATE')),
    constraint ck_repo_status check (status in ('ACTIVE', 'SYNCING', 'DISCONNECTED', 'FAILED', 'ARCHIVED'))
);
create index idx_repo_org on repositories (organization_id);
create index idx_repo_connection on repositories (git_provider_connection_id);
create index idx_repo_status on repositories (status);

-- 3.6 / 4.7 auth_refresh_tokens --------------------------------------------
-- ADR-009: short-lived stateless access JWT + hashed refresh tokens (hash only, never plaintext).
create table auth_refresh_tokens (
    id                   uuid        primary key,
    user_id              uuid        not null,
    token_hash           text        not null,
    issued_at            timestamptz not null,
    expires_at           timestamptz not null,
    revoked_at           timestamptz,
    replaced_by_token_id uuid,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    constraint uq_refresh_token_hash unique (token_hash),
    constraint fk_refresh_user foreign key (user_id) references users (id),
    constraint fk_refresh_replaced_by foreign key (replaced_by_token_id) references auth_refresh_tokens (id)
);
create index idx_refresh_user on auth_refresh_tokens (user_id);
create index idx_refresh_expiry on auth_refresh_tokens (expires_at);
