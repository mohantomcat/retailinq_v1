alter table recon.exception_integration_channels
    add column if not exists inbound_sync_enabled boolean not null default false,
    add column if not exists inbound_shared_secret varchar(255),
    add column if not exists auto_create_on_case_open boolean not null default false,
    add column if not exists auto_create_on_escalation boolean not null default false;

alter table recon.exception_external_tickets
    add column if not exists external_status varchar(64),
    add column if not exists last_external_update_at timestamp,
    add column if not exists last_external_updated_by varchar(255),
    add column if not exists last_external_comment text;

create table if not exists recon.exception_external_ticket_sync_events (
    id uuid primary key,
    ticket_id uuid not null references recon.exception_external_tickets(id) on delete cascade,
    tenant_id varchar(255) not null,
    channel_id uuid references recon.exception_integration_channels(id),
    case_id uuid references recon.exception_cases(id),
    transaction_key varchar(255),
    incident_key varchar(255),
    recon_view varchar(64),
    store_id varchar(64),
    external_reference varchar(255),
    event_type varchar(64) not null,
    external_status varchar(64),
    status_note text,
    external_updated_by varchar(255),
    payload text,
    synced_at timestamp not null,
    created_at timestamp not null default now()
);

create index if not exists idx_exception_external_ticket_sync_events_ticket
    on recon.exception_external_ticket_sync_events(ticket_id, synced_at desc);

create index if not exists idx_exception_external_ticket_sync_events_case
    on recon.exception_external_ticket_sync_events(case_id, synced_at desc);
