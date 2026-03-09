package com.github.access_report.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response returned by GET /api/v1/report
 *
 * Full response structure:
 * {
 *   "organization":      "my-company",
 *   "generatedAt":       "2024-03-07T10:30:00Z",
 *   "totalRepositories": 42,
 *   "totalUsers":        120,
 *   "users": [
 *     {
 *       "username":          "alice",
 *       "profileUrl":        "https://github.com/alice",
 *       "totalRepositories": 3,
 *       "repositories": [
 *         { "repository": "backend-api", "accessLevel": "admin", ... },
 *         { "repository": "frontend",    "accessLevel": "write", ... }
 *       ]
 *     },
 *     ...
 *   ]
 * }
 */
@Data
@Builder
public class AccessReport {

    /** GitHub organization name */
    private String organization;

    /** UTC timestamp when this report was generated */
    private Instant generatedAt;

    /** Total repos scanned in this org */
    private int totalRepositories;

    /** Total unique human users found across all repos */
    private int totalUsers;

    /**
     * The actual report data — each entry is one user + their repo list.
     * Sorted alphabetically by username for easy reading.
     */
    private List<UserAccess> users;
}