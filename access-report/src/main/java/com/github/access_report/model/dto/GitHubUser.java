package com.github.access_report.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the JSON response from:
 *   GET /orgs/{org}/members
 *
 * Represents a member of the GitHub organization.
 * This is different from GitHubCollaborator — a User is an org-level member,
 * while a Collaborator is scoped to a specific repository.
 *
 * We use this to cross-check: some users may be org members but have
 * access to zero repos, or vice versa (outside collaborators).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUser {

    private Long id;

    /** GitHub username — e.g. "john-doe" */
    private String login;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("html_url")
    private String htmlUrl;

    /**
     * "User" = human account
     * "Bot"  = automated bot (e.g. dependabot, github-actions)
     */
    private String type;

    /**
     * Convenience method — bots should be excluded from access reports
     * since they are service accounts, not real people.
     */
    public boolean isBot() {
        return "Bot".equalsIgnoreCase(this.type);
    }
}