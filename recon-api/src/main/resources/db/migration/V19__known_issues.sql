create table if not exists recon.known_issues (
    id uuid primary key,
    tenant_id varchar(100) not null,
    issue_key varchar(120) not null,
    title varchar(255) not null,
    issue_summary text,
    recon_view varchar(100),
    recon_status varchar(120),
    reason_code varchar(120),
    root_cause_category varchar(120),
    store_id varchar(80),
    match_keywords text,
    probable_cause text not null,
    recommended_action text not null,
    escalation_guidance text,
    resolver_notes text,
    priority_weight integer not null default 100,
    active boolean not null default true,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint uk_known_issues_tenant_issue_key unique (tenant_id, issue_key)
);

create index if not exists idx_known_issues_tenant_active
    on recon.known_issues (tenant_id, active, recon_view, priority_weight desc);

create table if not exists recon.known_issue_feedback (
    id uuid primary key,
    tenant_id varchar(100) not null,
    known_issue_id uuid not null references recon.known_issues (id) on delete cascade,
    context_key varchar(255) not null,
    transaction_key varchar(255),
    recon_view varchar(100),
    incident_key varchar(255),
    store_id varchar(80),
    source_view varchar(120),
    helpful boolean not null,
    feedback_notes text,
    created_by varchar(120) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint uk_known_issue_feedback_context unique (tenant_id, known_issue_id, created_by, context_key)
);

create index if not exists idx_known_issue_feedback_tenant_issue
    on recon.known_issue_feedback (tenant_id, known_issue_id, helpful);
