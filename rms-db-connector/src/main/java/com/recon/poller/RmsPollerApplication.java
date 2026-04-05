package com.recon.rms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class}
)
@EnableScheduling
public class RmsPollerApplication {

    static {
        // Always UTC internally.
        // Fixes Asia/Calcutta rejection from PostgreSQL.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(
                RmsPollerApplication.class, args);
    }
}

