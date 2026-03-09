package com.github.access_report.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * All GitHub-related config lives here.
 * Bound automatically from application.yml via @ConfigurationProperties.
 *
 * Values come from environment variables:
 *   GITHUB_TOKEN → github.token
 *   GITHUB_ORG   → github.org
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    @NotBlank(message = "GitHub token is required — set GITHUB_TOKEN env variable")
    private String token;

    @NotBlank(message = "GitHub org is required — set GITHUB_ORG env variable")
    private String org;

    private String baseUrl = "https://api.github.com";

    @Positive
    private int perPage = 100;

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
}