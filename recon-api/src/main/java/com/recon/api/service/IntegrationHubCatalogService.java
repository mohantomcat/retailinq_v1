package com.recon.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationHubCatalogService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureCatalog(String tenantId) {
        for (ConnectorSeed seed : connectorSeeds()) {
            UUID connectorId = upsertConnector(tenantId, seed);
            upsertFlow(tenantId, connectorId, seed);
        }
    }

    private UUID upsertConnector(String tenantId, ConnectorSeed seed) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.queryForObject("""
                insert into recon.integration_connector_definition (
                    id, tenant_id, connector_key, connector_label, connector_type,
                    source_system, target_system, module_key, enabled, runtime_mode,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?)
                on conflict (tenant_id, connector_key) do update set
                    connector_label = excluded.connector_label,
                    connector_type = excluded.connector_type,
                    source_system = excluded.source_system,
                    target_system = excluded.target_system,
                    module_key = excluded.module_key,
                    runtime_mode = excluded.runtime_mode,
                    updated_at = excluded.updated_at
                returning id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                UUID.randomUUID(),
                tenantId,
                seed.connectorKey(),
                seed.connectorLabel(),
                seed.connectorType(),
                seed.sourceSystem(),
                seed.targetSystem(),
                seed.moduleKey(),
                seed.runtimeMode(),
                now,
                now
        );
    }

    private void upsertFlow(String tenantId, UUID connectorId, ConnectorSeed seed) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into recon.integration_flow_definition (
                    id, tenant_id, connector_definition_id, flow_key, flow_label,
                    message_type, source_system, target_system, business_object,
                    enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                on conflict (tenant_id, flow_key) do update set
                    connector_definition_id = excluded.connector_definition_id,
                    flow_label = excluded.flow_label,
                    message_type = excluded.message_type,
                    source_system = excluded.source_system,
                    target_system = excluded.target_system,
                    business_object = excluded.business_object,
                    updated_at = excluded.updated_at
                """,
                UUID.randomUUID(),
                tenantId,
                connectorId,
                seed.flowKey(),
                seed.flowLabel(),
                seed.messageType(),
                seed.sourceSystem(),
                seed.targetSystem(),
                seed.businessObject(),
                now,
                now
        );
    }

    private List<ConnectorSeed> connectorSeeds() {
        return List.of(
                new ConnectorSeed(
                        "xstore-publisher",
                        "Xstore Publisher",
                        "DATABASE_PUBLISHER",
                        "XSTORE",
                        "RECON",
                        "xstore-publisher",
                        "BATCH",
                        "xstore-pos-transaction",
                        "Xstore POS transaction feed",
                        "POS_TRANSACTION",
                        "POS_TRANSACTION"
                ),
                new ConnectorSeed(
                        "sim-poller",
                        "SIM Poller",
                        "DATABASE_POLLING",
                        "SIM",
                        "RECON",
                        "sim-poller",
                        "POLLING",
                        "sim-target-status-poll",
                        "SIM target status poll",
                        "TARGET_STATUS_POLL",
                        "POS_TRANSACTION_STATUS"
                ),
                new ConnectorSeed(
                        "siocs-cloud-main",
                        "SIOCS Cloud Connector",
                        "ORDS_POLLING",
                        "SIOCS",
                        "RECON",
                        "siocs-cloud-connector",
                        "POLLING",
                        "siocs-canonical-transaction",
                        "SIOCS canonical transaction feed",
                        "CANONICAL_TRANSACTION",
                        "INVENTORY_TRANSACTION"
                ),
                new ConnectorSeed(
                        "mfcs-rds-main",
                        "MFCS RDS Connector",
                        "ORDS_POLLING",
                        "MFCS",
                        "RECON",
                        "mfcs-rds-connector",
                        "POLLING",
                        "mfcs-canonical-transaction",
                        "MFCS canonical transaction feed",
                        "CANONICAL_TRANSACTION",
                        "INVENTORY_TRANSACTION"
                ),
                new ConnectorSeed(
                        "rms-db-main",
                        "RMS DB Connector",
                        "DATABASE_POLLING",
                        "RMS",
                        "RECON",
                        "rms-db-connector",
                        "POLLING",
                        "rms-canonical-transaction",
                        "RMS canonical transaction feed",
                        "CANONICAL_TRANSACTION",
                        "INVENTORY_TRANSACTION"
                ),
                new ConnectorSeed(
                        "xocs-cloud-main",
                        "XOCS Cloud Connector",
                        "ORDS_POLLING",
                        "XOCS",
                        "RECON",
                        "xocs-cloud-connector",
                        "POLLING",
                        "xocs-pos-transaction",
                        "XOCS POS transaction feed",
                        "POS_TRANSACTION",
                        "POS_TRANSACTION"
                )
        );
    }

    private record ConnectorSeed(String connectorKey,
                                 String connectorLabel,
                                 String connectorType,
                                 String sourceSystem,
                                 String targetSystem,
                                 String moduleKey,
                                 String runtimeMode,
                                 String flowKey,
                                 String flowLabel,
                                 String messageType,
                                 String businessObject) {
    }
}
