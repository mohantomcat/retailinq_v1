alter table recon.exception_cases
    add column if not exists escalation_state varchar(32) not null default 'NONE',
    add column if not exists escalation_count integer not null default 0,
    add column if not exists last_escalated_by varchar(120),
    add column if not exists last_escalated_at timestamp,
    add column if not exists escalation_policy_id uuid,
    add column if not exists escalation_policy_name varchar(160),
    add column if not exists escalation_reason text,
    add column if not exists reopen_reason text,
    add column if not exists reopen_count integer not null default 0,
    add column if not exists last_reopened_by varchar(120),
    add column if not exists last_reopened_at timestamp;

create table if not exists recon.exception_escalation_policies (
    id uuid primary key,
    tenant_id varchar(100) not null,
    policy_name varchar(160) not null,
    recon_view varchar(60) not null,
    min_severity varchar(32),
    min_impact_score integer,
    trigger_on_sla_breach boolean not null default false,
    aging_hours integer,
    inactivity_hours integer,
    escalate_to_username varchar(120),
    escalate_to_role_name varchar(120),
    target_severity varchar(32),
    note_template text,
    active boolean not null default true,
    description text,
    created_by varchar(120) not null,
    updated_by varchar(120),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_exception_escalation_policies_scope
    on recon.exception_escalation_policies (tenant_id, recon_view, active, updated_at desc);

create table if not exists recon.exception_case_audit_events (
    id uuid primary key,
    exception_case_id uuid not null references recon.exception_cases(id) on delete cascade,
    tenant_id varchar(100) not null,
    transaction_key varchar(160) not null,
    recon_view varchar(60) not null,
    source_type varchar(40) not null,
    event_type varchar(80) not null,
    title varchar(160) not null,
    summary text,
    actor varchar(120),
    status varchar(80),
    changed_fields text,
    before_snapshot text,
    after_snapshot text,
    created_at timestamp not null default now()
);

create index if not exists idx_exception_case_audit_events_case
    on recon.exception_case_audit_events (exception_case_id, created_at desc);

create index if not exists idx_exception_case_audit_events_context
    on recon.exception_case_audit_events (tenant_id, transaction_key, recon_view, created_at desc);
