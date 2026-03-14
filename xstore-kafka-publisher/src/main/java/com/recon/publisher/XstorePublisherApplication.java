package com.recon.publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class XstorePublisherApplication {

    static {
        // Always UTC internally.
        // Tenant local time conversion happens only at API layer.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(
                XstorePublisherApplication.class, args);
    }
}