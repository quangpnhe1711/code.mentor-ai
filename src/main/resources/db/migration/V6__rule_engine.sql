-- Rule Engine foundation (FR-009/FR-010, BR-RUL-001..004, BR-RUL-006).
-- Rules are organization-scoped, may be natural language, have a default severity, and can be
-- enabled/disabled. Repository activation is represented by a single active assignment.

create table rule_sets (
    id                  uuid        primary key,
    organization_id     uuid        not null,
    name                text        not null,
    description         text,
    created_by_user_id  uuid        not null,
    archived            boolean     not null default false,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_rule_set_organization foreign key (organization_id) references organizations (id),
    constraint fk_rule_set_created_by foreign key (created_by_user_id) references users (id),
    constraint ck_rule_set_name_not_blank check (length(btrim(name)) > 0)
);
create index idx_rule_sets_organization_id on rule_sets (organization_id);

create table rules (
    id                  uuid        primary key,
    organization_id     uuid        not null,
    rule_set_id         uuid        not null,
    rule_key            text        not null,
    title               text        not null,
    instruction         text        not null,
    category            text        not null,
    default_severity    text        not null,
    enabled             boolean     not null default true,
    custom              boolean     not null default true,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_rule_organization foreign key (organization_id) references organizations (id),
    constraint fk_rule_rule_set foreign key (rule_set_id) references rule_sets (id),
    constraint ck_rule_key_not_blank check (length(btrim(rule_key)) > 0),
    constraint ck_rule_title_not_blank check (length(btrim(title)) > 0),
    constraint ck_rule_instruction_not_blank check (length(btrim(instruction)) > 0),
    constraint ck_rule_category_not_blank check (length(btrim(category)) > 0),
    constraint ck_rule_default_severity check (default_severity in ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);
create unique index ux_rules_rule_set_rule_key on rules (rule_set_id, rule_key);
create index idx_rules_organization_id on rules (organization_id);
create index idx_rules_rule_set_id on rules (rule_set_id);
create index idx_rules_enabled on rules (enabled);

create table repository_rule_set_assignments (
    id                  uuid        primary key,
    organization_id     uuid        not null,
    repository_id       uuid        not null,
    rule_set_id         uuid        not null,
    active              boolean     not null default true,
    assigned_by_user_id uuid        not null,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint fk_repo_rule_set_assignment_organization foreign key (organization_id) references organizations (id),
    constraint fk_repo_rule_set_assignment_repository foreign key (repository_id) references repositories (id),
    constraint fk_repo_rule_set_assignment_rule_set foreign key (rule_set_id) references rule_sets (id),
    constraint fk_repo_rule_set_assignment_assigned_by foreign key (assigned_by_user_id) references users (id)
);
create unique index ux_active_rule_set_per_repository
    on repository_rule_set_assignments (repository_id)
    where active = true;
create index idx_repo_rule_set_assignments_org_repo
    on repository_rule_set_assignments (organization_id, repository_id);
