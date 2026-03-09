package com.github.access_report.controller;

import com.github.access_report.model.response.AccessReport;
import com.github.access_report.model.response.UserAccess;
import com.github.access_report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller — exposes the access report endpoints.
 *
 * This class is intentionally thin.
 * It only handles HTTP concerns:
 *   - routing (which URL maps to which method)
 *   - status codes (200, 404)
 *   - response wrapping
 *
 * ALL business logic lives in ReportService.
 * This follows the Single Responsibility Principle.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Full access report for the entire organization.
     *
     * GET /api/v1/report
     *
     * Returns all users and the repos they have access to.
     * Response is sorted alphabetically by username.
     */
    @GetMapping("/report")
    public ResponseEntity<AccessReport> getFullReport() {
        log.info("GET /api/v1/report called");
        AccessReport report = reportService.generateReport();
        return ResponseEntity.ok(report);
    }

    /**
     * Access report for a single specific user.
     *
     * GET /api/v1/report/users/{username}
     *
     * Useful for auditing one employee's access.
     * Returns 404 if the username is not found in any repository.
     */
    @GetMapping("/report/users/{username}")
    public ResponseEntity<?> getUserReport(@PathVariable String username) {
        log.info("GET /api/v1/report/users/{} called", username);

        AccessReport fullReport = reportService.generateReport();

        // Find the user in the report
        UserAccess userAccess = fullReport.getUsers().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);

        if (userAccess == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "organization",      fullReport.getOrganization(),
                "generatedAt",       fullReport.getGeneratedAt(),
                "username",          userAccess.getUsername(),
                "profileUrl",        userAccess.getProfileUrl(),
                "totalRepositories", userAccess.getTotalRepositories(),
                "repositories",      userAccess.getRepositories()
        ));
    }
}