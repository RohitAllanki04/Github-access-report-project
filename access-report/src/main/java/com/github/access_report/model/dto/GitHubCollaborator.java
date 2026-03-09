package com.github.access_report.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps the JSON response from:
 *   GET /repos/{org}/{repo}/collaborators
 *
 * A Collaborator = a user who has access to a specific repository.
 * The nested Permissions object tells us exactly what they can do.
 *
 * Difference from GitHubUser:
 *   GitHubUser  → org-level member (belongs to the organization)
 *   GitHubCollaborator → repo-level access (can be outside collaborators too)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCollaborator {

    private Long id;

    /** GitHub username */
    private String login;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("html_url")
    private String htmlUrl;

    /**
     * "User" = human
     * "Bot"  = automated service account (e.g. dependabot[bot])
     */
    private String type;

    /** Granular permission flags returned by GitHub for this specific repo */
    private Permissions permissions;

    /**
     * Converts GitHub's boolean permission flags into a single readable string.
     *
     * GitHub permission hierarchy (highest → lowest):
     *   admin    → full control including deleting the repo
     *   maintain → manage repo settings, cannot delete
     *   push     → write access (push code, merge PRs)
     *   triage   → manage issues/PRs, cannot push code
     *   pull     → read-only (clone, view)
     */
    public String getAccessLevel() {
        if (permissions == null)    return "unknown";
        if (permissions.isAdmin())    return "admin";
        if (permissions.isMaintain()) return "maintain";
        if (permissions.isPush())     return "write";
        if (permissions.isTriage())   return "triage";
        if (permissions.isPull())     return "read";
        return "none";
    }

    public boolean isBot() {
        return "Bot".equalsIgnoreCase(this.type);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Permissions {
        private boolean admin;
        private boolean maintain;
        private boolean push;
        private boolean triage;
        private boolean pull;
    }
}