package com.recon.publisher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class XstoreRuntimeConfigService {

    private final JdbcTemplate runtimeConfigJdbcTemplate;

    public XstoreRuntimeConfigService(@Value("${runtime-config.datasource.url}") String url,
                                      @Value("${runtime-config.datasource.username}") String username,
                                      @Value("${runtime-config.datasource.password}") String password,
                                      @Value("${runtime-config.datasource.driver-class-name}") String driverClassName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.runtimeConfigJdbcTemplate = new JdbcTemplate(dataSource);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = findOverride(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public int getInt(String key, int defaultValue) {
        String value = findOverride(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid integer override for {}: {}", key, value);
            return defaultValue;
        }
    }

    private String findOverride(String key) {
        try {
            return runtimeConfigJdbcTemplate.query(
                    "select override_value from recon.configuration_overrides where config_key = ?",
                    ps -> ps.setString(1, key),
                    rs -> rs.next() ? rs.getString("override_value") : null
            );
        } catch (Exception ex) {
            log.debug("Runtime config lookup failed for {}: {}", key, ex.getMessage());
            return null;
        }
    }
}
