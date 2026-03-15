create table if not exists recon.configuration_overrides (
    config_key varchar(200) primary key,
    override_value text not null,
    updated_by varchar(200) not null,
    updated_at timestamp not null default now()
);

create table if not exists recon.configuration_change_audit (
    id bigserial primary key,
    config_key varchar(200) not null,
    old_value text null,
    new_value text null,
    changed_by varchar(200) not null,
    changed_at timestamp not null default now(),
    action varchar(30) not null
);

create index if not exists idx_configuration_change_audit_key_changed_at
    on recon.configuration_change_audit(config_key, changed_at desc);
