create table if not exists recon.exception_integration_channels (
    id uuid primary key,
    tenant_id varchar(100) not null,
    channel_name varchar(180) not null,
    channel_type varchar(80) not null,
    channel_group varchar(40) not null,
    recon_view varchar(100),
    endpoint_url varchar(1000),
    recipient_email varchar(320),
    headers_json text,
    default_project_key varchar(120),
    default_issue_type varchar(120),
    description text,
    active boolean not null default true,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_exception_integration_channels_tenant
    on recon.exception_integration_channels (tenant_id, active, channel_group, recon_view);

create table if not exists recon.exception_external_tickets (
    id uuid primary key,
    tenant_id varchar(100) not null,
    case_id uuid references recon.exception_cases (id) on delete set null,
    transaction_key varchar(255),
    incident_key varchar(255),
    incident_title varchar(255),
    recon_view varchar(100) not null,
    store_id varchar(80),
    channel_id uuid references recon.exception_integration_channels (id) on delete set null,
    channel_name varchar(180) not null,
    channel_type varchar(80) not null,
    ticket_summary varchar(255) not null,
    ticket_description text,
    external_reference varchar(255),
    external_url varchar(2000),
    delivery_status varchar(40) not null,
    response_status_code integer,
    request_payload text,
    response_payload text,
    error_message text,
    created_by varchar(120) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    last_synced_at timestamp
);

create index if not exists idx_exception_external_tickets_case
    on recon.exception_external_tickets (case_id, created_at desc);

create index if not exists idx_exception_external_tickets_incident
    on recon.exception_external_tickets (tenant_id, incident_key, created_at desc);

create table if not exists recon.exception_outbound_communications (
    id uuid primary key,
    tenant_id varchar(100) not null,
    case_id uuid references recon.exception_cases (id) on delete set null,
    transaction_key varchar(255),
    incident_key varchar(255),
    incident_title varchar(255),
    recon_view varchar(100) not null,
    store_id varchar(80),
    channel_id uuid references recon.exception_integration_channels (id) on delete set null,
    channel_name varchar(180) not null,
    channel_type varchar(80) not null,
    recipient varchar(1000),
    subject varchar(255),
    message_body text not null,
    delivery_status varchar(40) not null,
    response_status_code integer,
    request_payload text,
    response_payload text,
    error_message text,
    created_by varchar(120) not null,
    created_at timestamp not null default now(),
    delivered_at timestamp
);

create index if not exists idx_exception_outbound_communications_case
    on recon.exception_outbound_communications (case_id, created_at desc);

create index if not exists idx_exception_outbound_communications_incident
    on recon.exception_outbound_communications (tenant_id, incident_key, created_at desc);
