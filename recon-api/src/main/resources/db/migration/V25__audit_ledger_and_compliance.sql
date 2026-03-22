create table if not exists recon.audit_ledger_entries (
    id uuid primary key,
    entry_number bigserial not null,
    tenant_id varchar(100) not null,
    source_type varchar(40) not null,
    module_key varchar(80) not null,
    entity_type varchar(80) not null,
    entity_key varchar(200) not null,
    action_type varchar(80) not null,
    title varchar(160) not null,
    summary text,
    actor varchar(120),
    reason text,
    status varchar(80),
    reference_key varchar(200),
    control_family varchar(80),
    evidence_tags varchar(400),
    before_state text,
    after_state text,
    metadata_json text,
    event_at timestamp not null default now(),
    previous_hash varchar(128),
    event_hash varchar(128) not null
);

create unique index if not exists uq_audit_ledger_entries_entry_number
    on recon.audit_ledger_entries (entry_number);

create index if not exists idx_audit_ledger_entries_tenant_event
    on recon.audit_ledger_entries (tenant_id, event_at desc);

create index if not exists idx_audit_ledger_entries_tenant_source
    on recon.audit_ledger_entries (tenant_id, source_type, module_key, event_at desc);

create index if not exists idx_audit_ledger_entries_tenant_actor
    on recon.audit_ledger_entries (tenant_id, actor, event_at desc);

create table if not exists recon.audit_retention_policies (
    id uuid primary key,
    tenant_id varchar(100) not null unique,
    policy_name varchar(160) not null,
    minimum_retention_days integer not null default 2555,
    archive_after_days integer not null default 90,
    legal_hold boolean not null default false,
    default_export_format varchar(20) not null default 'CSV',
    notes text,
    created_by varchar(120) not null,
    updated_by varchar(120),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table if not exists recon.audit_archive_batches (
    id uuid primary key,
    tenant_id varchar(100) not null,
    policy_name varchar(160),
    archive_reason varchar(120) not null,
    export_format varchar(20) not null default 'JSON',
    from_event_at timestamp,
    to_event_at timestamp,
    entry_count integer not null default 0,
    created_by varchar(120) not null,
    created_at timestamp not null default now()
);

create index if not exists idx_audit_archive_batches_tenant_created
    on recon.audit_archive_batches (tenant_id, created_at desc);

create table if not exists recon.audit_ledger_archive_entries (
    id uuid primary key,
    archive_batch_id uuid not null references recon.audit_archive_batches(id),
    original_entry_id uuid not null unique,
    original_entry_number bigint not null,
    tenant_id varchar(100) not null,
    source_type varchar(40) not null,
    module_key varchar(80) not null,
    entity_type varchar(80) not null,
    entity_key varchar(200) not null,
    action_type varchar(80) not null,
    title varchar(160) not null,
    summary text,
    actor varchar(120),
    reason text,
    status varchar(80),
    reference_key varchar(200),
    control_family varchar(80),
    evidence_tags varchar(400),
    before_state text,
    after_state text,
    metadata_json text,
    event_at timestamp not null,
    previous_hash varchar(128),
    event_hash varchar(128) not null,
    archived_at timestamp not null default now()
);

create index if not exists idx_audit_ledger_archive_entries_tenant_event
    on recon.audit_ledger_archive_entries (tenant_id, event_at desc);

create index if not exists idx_audit_ledger_archive_entries_batch
    on recon.audit_ledger_archive_entries (archive_batch_id, event_at desc);

insert into recon.audit_retention_policies (
    id,
    tenant_id,
    policy_name,
    minimum_retention_days,
    archive_after_days,
    legal_hold,
    default_export_format,
    notes,
    created_by,
    updated_by
)
select
    (substr(md5('audit-policy|' || tenant_id), 1, 8)
        || '-' || substr(md5('audit-policy|' || tenant_id), 9, 4)
        || '-' || substr(md5('audit-policy|' || tenant_id), 13, 4)
        || '-' || substr(md5('audit-policy|' || tenant_id), 17, 4)
        || '-' || substr(md5('audit-policy|' || tenant_id), 21, 12))::uuid,
    tenant_id,
    tenant_name || ' Audit Retention Policy',
    2555,
    90,
    false,
    'CSV',
    'Default compliance retention policy',
    'system',
    'system'
from recon.tenant_config
where active = true
on conflict (tenant_id) do nothing;

insert into recon.audit_ledger_entries (
    id,
    tenant_id,
    source_type,
    module_key,
    entity_type,
    entity_key,
    action_type,
    title,
    summary,
    actor,
    reason,
    status,
    reference_key,
    control_family,
    evidence_tags,
    before_state,
    after_state,
    metadata_json,
    event_at,
    previous_hash,
    event_hash
)
select
    (substr(md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '')), 1, 8)
        || '-' || substr(md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '')), 9, 4)
        || '-' || substr(md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '')), 13, 4)
        || '-' || substr(md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '')), 17, 4)
        || '-' || substr(md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '')), 21, 12))::uuid,
    coalesce((select tenant_id from recon.tenant_config where active = true order by tenant_id limit 1), 'tenant-india'),
    'CONFIGURATION',
    'CONFIGURATIONS',
    'CONFIGURATION_OVERRIDE',
    config_key,
    upper(action),
    'Configuration ' || upper(action),
    case
        when upper(action) = 'DELETE' then config_key || ' cleared'
        else config_key || ' updated'
    end,
    changed_by,
    null,
    upper(action),
    config_key,
    'ITGC',
    'CONFIG,CHANGE',
    old_value,
    new_value,
    null,
    changed_at,
    null,
    md5('cfg|' || config_key || '|' || changed_at::text || '|' || coalesce(changed_by, '') || '|' || coalesce(action, '') || '|' || coalesce(old_value, '') || '|' || coalesce(new_value, ''))
from recon.configuration_change_audit c
where not exists (
    select 1
    from recon.audit_ledger_entries l
    where l.event_hash = md5('cfg|' || c.config_key || '|' || c.changed_at::text || '|' || coalesce(c.changed_by, '') || '|' || coalesce(c.action, '') || '|' || coalesce(c.old_value, '') || '|' || coalesce(c.new_value, ''))
);

insert into recon.audit_ledger_entries (
    id,
    tenant_id,
    source_type,
    module_key,
    entity_type,
    entity_key,
    action_type,
    title,
    summary,
    actor,
    reason,
    status,
    reference_key,
    control_family,
    evidence_tags,
    before_state,
    after_state,
    metadata_json,
    event_at,
    previous_hash,
    event_hash
)
select
    (substr(md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '')), 1, 8)
        || '-' || substr(md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '')), 9, 4)
        || '-' || substr(md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '')), 13, 4)
        || '-' || substr(md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '')), 17, 4)
        || '-' || substr(md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '')), 21, 12))::uuid,
    tenant_id,
    'OPERATIONS',
    coalesce(recon_view, 'OPERATIONS'),
    'OPERATIONS_ACTION',
    coalesce(transaction_key, module_id),
    upper(action_key),
    case when playbook_step_title is not null and playbook_step_title <> '' then 'Playbook action executed' else 'Operations action executed' end,
    coalesce(result_message, module_id || ' -> ' || action_key),
    requested_by,
    null,
    result_status,
    coalesce(transaction_key, module_id),
    'OPERATIONS',
    'OPERATIONS,ACTION',
    request_payload,
    null,
    null,
    created_at,
    null,
    md5('ops|' || tenant_id || '|' || module_id || '|' || action_key || '|' || created_at::text || '|' || coalesce(requested_by, '') || '|' || coalesce(result_status, ''))
from recon.operations_action_audit o
where not exists (
    select 1
    from recon.audit_ledger_entries l
    where l.event_hash = md5('ops|' || o.tenant_id || '|' || o.module_id || '|' || o.action_key || '|' || o.created_at::text || '|' || coalesce(o.requested_by, '') || '|' || coalesce(o.result_status, ''))
);

insert into recon.audit_ledger_entries (
    id,
    tenant_id,
    source_type,
    module_key,
    entity_type,
    entity_key,
    action_type,
    title,
    summary,
    actor,
    reason,
    status,
    reference_key,
    control_family,
    evidence_tags,
    before_state,
    after_state,
    metadata_json,
    event_at,
    previous_hash,
    event_hash
)
select
    (substr(md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text), 1, 8)
        || '-' || substr(md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text), 9, 4)
        || '-' || substr(md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text), 13, 4)
        || '-' || substr(md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text), 17, 4)
        || '-' || substr(md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text), 21, 12))::uuid,
    tenant_id,
    'EXCEPTION',
    recon_view,
    'EXCEPTION_CASE',
    transaction_key,
    event_type,
    title,
    summary,
    actor,
    summary,
    status,
    transaction_key,
    'EXCEPTION_CONTROL',
    'EXCEPTION,CASE',
    before_snapshot,
    after_snapshot,
    changed_fields,
    created_at,
    null,
    md5('exc-audit|' || tenant_id || '|' || transaction_key || '|' || event_type || '|' || created_at::text || '|' || coalesce(actor, ''))
from recon.exception_case_audit_events e
where not exists (
    select 1
    from recon.audit_ledger_entries l
    where l.event_hash = md5('exc-audit|' || e.tenant_id || '|' || e.transaction_key || '|' || e.event_type || '|' || e.created_at::text || '|' || coalesce(e.actor, ''))
);

insert into recon.audit_ledger_entries (
    id,
    tenant_id,
    source_type,
    module_key,
    entity_type,
    entity_key,
    action_type,
    title,
    summary,
    actor,
    reason,
    status,
    reference_key,
    control_family,
    evidence_tags,
    before_state,
    after_state,
    metadata_json,
    event_at,
    previous_hash,
    event_hash
)
select
    (substr(md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '')), 1, 8)
        || '-' || substr(md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '')), 9, 4)
        || '-' || substr(md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '')), 13, 4)
        || '-' || substr(md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '')), 17, 4)
        || '-' || substr(md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '')), 21, 12))::uuid,
    e.tenant_id,
    'EXCEPTION',
    e.recon_view,
    'EXCEPTION_COMMENT',
    e.transaction_key,
    'COMMENT_ADDED',
    'Exception comment added',
    c.comment_text,
    c.created_by,
    null,
    'COMMENTED',
    e.transaction_key,
    'EXCEPTION_CONTROL',
    'EXCEPTION,COMMENT',
    null,
    c.comment_text,
    null,
    c.created_at,
    null,
    md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '') || '|' || coalesce(c.comment_text, ''))
from recon.exception_comments c
join recon.exception_cases e
  on e.id = c.case_id
where not exists (
    select 1
    from recon.audit_ledger_entries l
    where l.event_hash = md5('exc-comment|' || e.tenant_id || '|' || e.transaction_key || '|' || c.created_at::text || '|' || coalesce(c.created_by, '') || '|' || coalesce(c.comment_text, ''))
);

create or replace function recon.prevent_audit_record_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'Audit records are append-only and cannot be updated or deleted';
end;
$$;

drop trigger if exists trg_prevent_audit_ledger_entries_mutation on recon.audit_ledger_entries;
create trigger trg_prevent_audit_ledger_entries_mutation
before update or delete on recon.audit_ledger_entries
for each row execute function recon.prevent_audit_record_mutation();

drop trigger if exists trg_prevent_audit_archive_batches_mutation on recon.audit_archive_batches;
create trigger trg_prevent_audit_archive_batches_mutation
before update or delete on recon.audit_archive_batches
for each row execute function recon.prevent_audit_record_mutation();

drop trigger if exists trg_prevent_audit_ledger_archive_entries_mutation on recon.audit_ledger_archive_entries;
create trigger trg_prevent_audit_ledger_archive_entries_mutation
before update or delete on recon.audit_ledger_archive_entries
for each row execute function recon.prevent_audit_record_mutation();

insert into recon.permissions (id, code, name, module, description, created_at)
values
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4011'::uuid, 'AUDIT_EXPORT', 'Audit Export', 'AUDIT',
     'Export immutable audit evidence for auditors and external governance workflows.', now()),
    ('5d15c4b7-2e54-4d2e-9d3c-1208aa0c4012'::uuid, 'AUDIT_MANAGE', 'Audit Compliance Manage', 'AUDIT',
     'Manage audit retention policies and archive packages.', now())
on conflict (code) do nothing;

insert into recon.role_permissions (role_id, permission_id)
select r.id, p.id
from recon.roles r
join recon.permissions p
  on p.code in ('AUDIT_EXPORT', 'AUDIT_MANAGE')
where lower(r.name) = 'admin'
  and not exists (
      select 1
      from recon.role_permissions rp
      where rp.role_id = r.id
        and rp.permission_id = p.id
  );
