package com.github.access_report.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a single user's complete access profile.
 *
 * This is what each entry in the report looks like:
 * {
 *   "username": "john-doe",
 *   "profileUrl": "https://github.com/john-doe",
 *   "totalRepositories": 3,
 *   "repositories": [
 *     { "repository": "backend-api", "accessLevel": "write", ... },
 *     { "repository": "frontend",    "accessLevel": "read",  ... }
 *   ]
 * }
 *
 * Why a dedicated class instead of just Map<String, List<?>>?
 *   - Cleaner JSON output with metadata (totalRepositories)
 *   - Easier to extend later (add email, team info, last active, etc.)
 *   - Explicit contract — clients know exactly what fields to expect
 */
@Data
@Builder
public class UserAccess {

    /** GitHub username */
    private String username;

    /** Direct link to GitHub profile */
    private String profileUrl;

    /** Convenience count — how many repos this user can access */
    private int totalRepositories;

    /** All repositories this user has access to, sorted alphabetically */
    private List<RepositoryAccess> repositories;
}