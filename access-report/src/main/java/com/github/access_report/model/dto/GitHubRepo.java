package com.github.access_report.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the JSON response from:
 *   GET /orgs/{org}/repos
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) means:
 *   GitHub returns 80+ fields per repo — we only map what we need.
 *   If GitHub adds new fields later, this class won't break.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepo {

    private Long id;

    /** Short name — e.g. "backend-api" */
    private String name;

    /** Org + name — e.g. "my-company/backend-api" */
    @JsonProperty("full_name")
    private String fullName;

    /** true = private repo, false = public */
    @JsonProperty("private")
    private boolean privateRepo;

    /** Direct GitHub URL for this repo */
    @JsonProperty("html_url")
    private String htmlUrl;

    private String description;

    @JsonProperty("default_branch")
    private String defaultBranch;
}