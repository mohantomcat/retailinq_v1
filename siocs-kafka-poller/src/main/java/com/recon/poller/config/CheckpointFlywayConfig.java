package com.recon.poller.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
public class CheckpointFlywayConfig {

    private final DataSource checkpointDataSource;

    public CheckpointFlywayConfig(
            @Qualifier("checkpointDataSource")
            DataSource checkpointDataSource) {
        this.checkpointDataSource = checkpointDataSource;
    }

    @PostConstruct
    public void migrate() {
        Flyway.configure()
                .dataSource(checkpointDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
