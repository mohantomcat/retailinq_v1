package com.recon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ReconApiApplication {

    static {
        // API processes and stores in UTC.
        // Conversion to tenant timezone done only
        // in response serialisation layer.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(
                ReconApiApplication.class, args);
    }
}
