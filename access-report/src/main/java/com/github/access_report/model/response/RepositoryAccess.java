package com.github.access_report.model.response;

import lombok.Builder;
import lombok.Data;

/**
 * A single repository entry inside a user's access list.
 *
 * Example:
 * {
 *   "repository":    "backend-api",
 *   "fullName":      "my-company/backend-api",
 *   "accessLevel":   "write",
 *   "privateRepo":   true,
 *   "repositoryUrl": "https://github.com/my-company/backend-api"
 * }
 */
@Data
@Builder
public class RepositoryAccess {

    /** Short repo name */
    private String repository;

    /** Org-qualified name, e.g. "my-company/backend-api" */
    private String fullName;

    /**
     * Human-readable access level.
     * Possible values: admin | maintain | write | triage | read | unknown
     */
    private String accessLevel;

    private boolean privateRepo;

    private String repositoryUrl;
}