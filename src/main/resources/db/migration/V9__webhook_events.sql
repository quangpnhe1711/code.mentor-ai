-- Administration webhook intake foundation.
-- Stores provider event metadata and payload hash only; raw webhook payloads are not persisted.

create table webhook_events (
    id                  uuid        primary key,
    provider            text        not null,
    delivery_id         text        not null,
    event_type          text        not null,
    action              text,
    external_repo_id    text,
    repository_full_name text,
    payload_sha256      text        not null,
    signature_verified  boolean     not null,
    processing_status   text        not null,
    review_job_id       uuid,
    error_reason        text,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint uq_webhook_provider_delivery unique (provider, delivery_id),
    constraint fk_webhook_review_job foreign key (review_job_id) references review_jobs (id),
    constraint ck_webhook_provider check (provider in ('GITHUB')),
    constraint ck_webhook_processing_status check (processing_status in ('RECEIVED', 'IGNORED', 'PROCESSED', 'FAILED'))
);
create index idx_webhook_events_provider_repo on webhook_events (provider, external_repo_id);
create index idx_webhook_events_status on webhook_events (processing_status);
