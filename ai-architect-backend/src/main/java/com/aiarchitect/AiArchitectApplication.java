package com.aiarchitect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI Architect Backend — Autonomous Engineering Platform
 * Orchestrates 11-phase AI pipeline with multi-provider failover.
 */
@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class AiArchitectApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiArchitectApplication.class, args);
    }
}
