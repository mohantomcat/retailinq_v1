package com.recon.api.repository;

import com.recon.api.domain.TenantConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<TenantConfig> findById(String tenantId) {
        String sql = """
                SELECT tenant_id, tenant_name, timezone,
                       country_code, currency_code,
                       date_format, date_display_format, active
                FROM recon.tenant_config
                WHERE tenant_id = ?
                  AND active = TRUE
                """;
        try {
            TenantConfig config = jdbcTemplate.queryForObject(
                    sql,
                    (rs, rn) -> TenantConfig.builder()
                            .tenantId(rs.getString("tenant_id"))
                            .tenantName(rs.getString("tenant_name"))
                            .timezone(rs.getString("timezone"))
                            .countryCode(rs.getString("country_code"))
                            .currencyCode(rs.getString("currency_code"))
                            .dateFormat(rs.getString("date_format"))
                            .dateDisplayFormat(
                                    rs.getString("date_display_format"))
                            .active(rs.getBoolean("active"))
                            .build(),
                    tenantId);
            return Optional.ofNullable(config);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<TenantConfig> findAll() {
        String sql = """
                SELECT tenant_id, tenant_name, timezone,
                       country_code, currency_code,
                       date_format, date_display_format, active
                FROM recon.tenant_config
                WHERE active = TRUE
                ORDER BY tenant_name
                """;
        return jdbcTemplate.query(sql,
                (rs, rn) -> TenantConfig.builder()
                        .tenantId(rs.getString("tenant_id"))
                        .tenantName(rs.getString("tenant_name"))
                        .timezone(rs.getString("timezone"))
                        .countryCode(rs.getString("country_code"))
                        .currencyCode(rs.getString("currency_code"))
                        .dateFormat(rs.getString("date_format"))
                        .dateDisplayFormat(
                                rs.getString("date_display_format"))
                        .active(rs.getBoolean("active"))
                        .build());
    }
}