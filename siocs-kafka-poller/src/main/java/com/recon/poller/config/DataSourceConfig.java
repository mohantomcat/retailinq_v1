package com.recon.poller.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // ── Primary: Oracle SIOCS datasource ─────────────────────────

    @Primary
    @Bean(name = "siocsDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties siocsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "siocsDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource siocsDataSource(
            @Qualifier("siocsDataSourceProperties")
            DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "siocsJdbcTemplate")
    public JdbcTemplate siocsJdbcTemplate(
            @Qualifier("siocsDataSource")
            DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ── Secondary: PostgreSQL checkpoint datasource ───────────────

    @Bean(name = "checkpointDataSourceProperties")
    @ConfigurationProperties("checkpoint.datasource")
    public DataSourceProperties checkpointDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "checkpointDataSource")
    @ConfigurationProperties("checkpoint.datasource.hikari")
    public HikariDataSource checkpointDataSource(
            @Qualifier("checkpointDataSourceProperties")
            DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "checkpointJdbcTemplate")
    public JdbcTemplate checkpointJdbcTemplate(
            @Qualifier("checkpointDataSource")
            DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}