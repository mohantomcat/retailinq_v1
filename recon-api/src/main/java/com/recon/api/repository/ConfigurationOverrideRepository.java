package com.recon.api.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConfigurationOverrideRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, String> findAllOverrides() {
        return jdbcTemplate.query(
                "select config_key, override_value from recon.configuration_overrides order by config_key",
                rs -> {
                    Map<String, String> overrides = new LinkedHashMap<>();
                    while (rs.next()) {
                        overrides.put(rs.getString("config_key"), rs.getString("override_value"));
                    }
                    return overrides;
                }
        );
    }

    public Optional<String> findOverride(String configKey) {
        return jdbcTemplate.query(
                "select override_value from recon.configuration_overrides where config_key = ?",
                ps -> ps.setString(1, configKey),
                rs -> rs.next() ? Optional.ofNullable(rs.getString("override_value")) : Optional.empty()
        );
    }

    public void upsertOverride(String configKey, String overrideValue, String changedBy) {
        String oldValue = findOverride(configKey).orElse(null);
        jdbcTemplate.update("""
                insert into recon.configuration_overrides(config_key, override_value, updated_by, updated_at)
                values (?, ?, ?, now())
                on conflict (config_key)
                do update set override_value = excluded.override_value,
                              updated_by = excluded.updated_by,
                              updated_at = excluded.updated_at
                """, configKey, overrideValue, changedBy);
        insertAudit(configKey, oldValue, overrideValue, changedBy, "UPSERT");
    }

    public void deleteOverride(String configKey, String changedBy) {
        String oldValue = findOverride(configKey).orElse(null);
        int deleted = jdbcTemplate.update(
                "delete from recon.configuration_overrides where config_key = ?",
                configKey
        );
        if (deleted > 0) {
            insertAudit(configKey, oldValue, null, changedBy, "DELETE");
        }
    }

    private void insertAudit(String configKey, String oldValue, String newValue, String changedBy, String action) {
        jdbcTemplate.update("""
                insert into recon.configuration_change_audit(
                    config_key, old_value, new_value, changed_by, changed_at, action
                ) values (?, ?, ?, ?, now(), ?)
                """, configKey, oldValue, newValue, changedBy, action);
    }
}
