package com.recon.xocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@ConfigurationPropertiesScan
public class XocsCloudIngestionConnectorApplication {

    static {
        // Always keep runtime/database interaction in UTC.
        // This avoids PostgreSQL rejecting the host alias Asia/Calcutta.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(XocsCloudIngestionConnectorApplication.class, args);
    }
}
