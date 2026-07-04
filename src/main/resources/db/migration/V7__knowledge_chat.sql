-- Knowledge / repository Q&A foundation.
-- Stores chat metadata, generated answers, and citations only. Source content is never persisted.

create table chat_sessions (
    id                 uuid        primary key,
    organization_id    uuid        not null,
    repository_id      uuid        not null,
    snapshot_id        uuid,
    created_by_user_id uuid        not null,
    title              text        not null,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    constraint fk_chat_session_organization foreign key (organization_id) references organizations (id),
    constraint fk_chat_session_repository foreign key (repository_id) references repositories (id),
    constraint fk_chat_session_snapshot foreign key (snapshot_id) references repository_snapshots (id),
    constraint fk_chat_session_created_by foreign key (created_by_user_id) references users (id)
);
create index idx_chat_sessions_org_repo on chat_sessions (organization_id, repository_id);
create index idx_chat_sessions_created_by on chat_sessions (created_by_user_id);

create table chat_messages (
    id              uuid        primary key,
    chat_session_id uuid        not null,
    role            text        not null,
    content         text        not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint fk_chat_message_session foreign key (chat_session_id) references chat_sessions (id),
    constraint ck_chat_message_role check (role in ('USER', 'ASSISTANT'))
);
create index idx_chat_messages_session_created on chat_messages (chat_session_id, created_at);

create table citations (
    id              uuid        primary key,
    chat_message_id uuid        not null,
    file_path       text        not null,
    line_start      integer,
    line_end        integer,
    reason          text        not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint fk_citation_message foreign key (chat_message_id) references chat_messages (id)
);
create index idx_citations_message on citations (chat_message_id);
