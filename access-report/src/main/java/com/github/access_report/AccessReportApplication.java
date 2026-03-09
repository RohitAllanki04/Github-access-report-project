package com.github.access_report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching   // activates @Cacheable annotations
@EnableAsync     // activates @Async annotations for parallel calls
public class AccessReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessReportApplication.class, args);
    }
}