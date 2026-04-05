create table if not exists recon.recon_group_catalog (
    id uuid primary key,
    group_code varchar(64) not null unique,
    group_label varchar(128) not null,
    group_description varchar(512),
    display_order integer not null,
    selection_required boolean not null default true,
    is_active boolean not null default true,
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now()
);

insert into recon.recon_group_catalog (
    id,
    group_code,
    group_label,
    group_description,
    display_order,
    selection_required,
    is_active,
    created_at,
    updated_at
)
values
    (
        '9019f57b-4d0d-4ccf-a8c4-a894289fb001'::uuid,
        'POS_STORE_INVENTORY',
        'POS ↔ Store Inventory',
        'Choose the active point-of-sale to store inventory reconciliation lane for this tenant.',
        10,
        true,
        true,
        now(),
        now()
    ),
    (
        '9019f57b-4d0d-4ccf-a8c4-a894289fb002'::uuid,
        'POS_XOCS',
        'POS ↔ XOCS',
        'Enable the Xstore to XOCS reconciliation lane only for tenants that run this stack.',
        20,
        false,
        true,
        now(),
        now()
    ),
    (
        '9019f57b-4d0d-4ccf-a8c4-a894289fb003'::uuid,
        'STORE_INVENTORY_MERCH',
        'Store Inventory ↔ Merchandising',
        'Choose the active store inventory to merchandising reconciliation lane for this tenant.',
        30,
        true,
        true,
        now(),
        now()
    )
on conflict (group_code) do update set
    group_label = excluded.group_label,
    group_description = excluded.group_description,
    display_order = excluded.display_order,
    selection_required = excluded.selection_required,
    is_active = excluded.is_active,
    updated_at = now();

alter table if exists recon.recon_module_catalog
    add column if not exists group_code varchar(64);

update recon.recon_module_catalog
set group_code = case
    when recon_view in ('XSTORE_SIM', 'XSTORE_SIOCS') then 'POS_STORE_INVENTORY'
    when recon_view = 'XSTORE_XOCS' then 'POS_XOCS'
    when recon_view in ('SIM_RMS', 'SIOCS_MFCS', 'SIM_MFCS', 'SIOCS_RMS') then 'STORE_INVENTORY_MERCH'
    else group_code
end
where group_code is null
   or btrim(group_code) = '';

alter table recon.recon_module_catalog
    alter column group_code set not null;

create index if not exists idx_recon_module_catalog_group_code
    on recon.recon_module_catalog (group_code);

create table if not exists recon.tenant_group_selection (
    id uuid primary key,
    tenant_id varchar(64) not null,
    group_code varchar(64) not null,
    selected_recon_view varchar(64) not null,
    updated_by varchar(128),
    created_at timestamp without time zone not null default now(),
    updated_at timestamp without time zone not null default now(),
    constraint uk_tenant_group_selection unique (tenant_id, group_code)
);

create index if not exists idx_tenant_group_selection_tenant
    on recon.tenant_group_selection (tenant_id);

create index if not exists idx_tenant_group_selection_recon_view
    on recon.tenant_group_selection (selected_recon_view);
