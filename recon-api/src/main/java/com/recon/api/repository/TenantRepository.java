package com.recon.api.repository;

import com.recon.api.domain.TenantConfig;
import com.recon.api.domain.SaveTenantOperatingModelRequest;
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
                       locale_code, date_format, date_display_format,
                       week_start_day, business_days,
                       workday_start_time, workday_end_time,
                       holiday_calendar, active
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
                            .localeCode(rs.getString("locale_code"))
                            .dateFormat(rs.getString("date_format"))
                            .dateDisplayFormat(rs.getString("date_display_format"))
                            .weekStartDay(rs.getString("week_start_day"))
                            .businessDays(rs.getString("business_days"))
                            .workdayStartTime(rs.getString("workday_start_time"))
                            .workdayEndTime(rs.getString("workday_end_time"))
                            .holidayCalendar(rs.getString("holiday_calendar"))
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
                       locale_code, date_format, date_display_format,
                       week_start_day, business_days,
                       workday_start_time, workday_end_time,
                       holiday_calendar, active
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
                        .localeCode(rs.getString("locale_code"))
                        .dateFormat(rs.getString("date_format"))
                        .dateDisplayFormat(rs.getString("date_display_format"))
                        .weekStartDay(rs.getString("week_start_day"))
                        .businessDays(rs.getString("business_days"))
                        .workdayStartTime(rs.getString("workday_start_time"))
                        .workdayEndTime(rs.getString("workday_end_time"))
                        .holidayCalendar(rs.getString("holiday_calendar"))
                        .active(rs.getBoolean("active"))
                        .build());
    }

    public boolean updateOperatingModel(String tenantId, SaveTenantOperatingModelRequest request) {
        String sql = """
                UPDATE recon.tenant_config
                SET timezone = ?,
                    country_code = ?,
                    currency_code = ?,
                    locale_code = ?,
                    date_display_format = ?,
                    week_start_day = ?,
                    business_days = ?,
                    workday_start_time = ?,
                    workday_end_time = ?,
                    holiday_calendar = ?
                WHERE tenant_id = ?
                """;
        return jdbcTemplate.update(
                sql,
                request.getTimezone(),
                request.getCountryCode(),
                request.getCurrencyCode(),
                request.getLocaleCode(),
                request.getDateDisplayFormat(),
                request.getWeekStartDay(),
                request.getBusinessDays() == null ? null : String.join(",", request.getBusinessDays()),
                request.getWorkdayStartTime(),
                request.getWorkdayEndTime(),
                request.getHolidayDates() == null ? null : String.join(",", request.getHolidayDates()),
                tenantId
        ) > 0;
    }
}
