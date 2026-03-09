package com.github.access_report.service;

import com.github.access_report.config.GitHubProperties;
import com.github.access_report.model.dto.GitHubCollaborator;
import com.github.access_report.model.dto.GitHubRepo;
import com.github.access_report.model.response.AccessReport;
import com.github.access_report.model.response.RepositoryAccess;
import com.github.access_report.model.response.UserAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates the full report generation pipeline.
 *
 * Pipeline:
 *  Step 1 — Fetch all repos (one paginated call, sequential)
 *  Step 2 — Fire one async collaborator call per repo (all in parallel)
 *  Step 3 — Wait for all async calls to finish (CompletableFuture.allOf)
 *  Step 4 — Aggregate: invert repo→[users] into user→[repos]
 *  Step 5 — Build UserAccess objects and wrap in AccessReport
 *
 * Why separation from GitHubService?
 *   GitHubService = knows how to TALK to GitHub
 *   ReportService = knows how to AGGREGATE the data
 *   Single Responsibility Principle — each class has one job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final GitHubService gitHubService;
    private final GitHubProperties props;

    public AccessReport generateReport() {
        long start = System.currentTimeMillis();
        log.info("Starting report generation for org: {}", props.getOrg());

        // ── Step 1: Fetch all repositories (sequential, paginated) ──────────
        List<GitHubRepo> repos = gitHubService.fetchAllRepositories();
        log.info("Found {} repositories. Firing async collaborator calls...", repos.size());

        // ── Step 2: Fire ALL collaborator requests in PARALLEL ───────────────
        // We do NOT wait here — each call runs in its own thread.
        // futureMap holds: repo name → CompletableFuture<List<Collaborator>>
        Map<String, CompletableFuture<List<GitHubCollaborator>>> futureMap = new LinkedHashMap<>();
        for (GitHubRepo repo : repos) {
            futureMap.put(repo.getName(), gitHubService.fetchCollaboratorsAsync(repo.getName()));
        }

        // ── Step 3: Wait for ALL futures to complete ─────────────────────────
        // CompletableFuture.allOf() creates a new Future that completes
        // only when every single future in the array is done.
        CompletableFuture.allOf(
                futureMap.values().toArray(new CompletableFuture[0])
        ).join(); // .join() blocks here until everything is ready

        // ── Step 4: Build a repo lookup map (name → GitHubRepo object) ───────
        Map<String, GitHubRepo> repoLookup = repos.stream()
                .collect(Collectors.toMap(GitHubRepo::getName, r -> r));

        // ── Step 5: Aggregate — invert repo→[users] to user→[repos] ─────────
        // intermediateMap: username → list of RepositoryAccess objects
        Map<String, List<RepositoryAccess>> intermediateMap = new HashMap<>();

        for (Map.Entry<String, CompletableFuture<List<GitHubCollaborator>>> entry : futureMap.entrySet()) {
            String repoName = entry.getKey();
            List<GitHubCollaborator> collaborators = entry.getValue().join(); // already done, no wait
            GitHubRepo repo = repoLookup.get(repoName);

            for (GitHubCollaborator collaborator : collaborators) {

                // Skip bots — they are service accounts, not real people
                if (collaborator.isBot()) {
                    log.debug("Skipping bot account: {}", collaborator.getLogin());
                    continue;
                }

                RepositoryAccess repoAccess = RepositoryAccess.builder()
                        .repository(repo.getName())
                        .fullName(repo.getFullName())
                        .accessLevel(collaborator.getAccessLevel())
                        .privateRepo(repo.isPrivateRepo())
                        .repositoryUrl(repo.getHtmlUrl())
                        .build();

                // computeIfAbsent: create the list if this is the first repo for this user
                intermediateMap
                        .computeIfAbsent(collaborator.getLogin(), k -> new ArrayList<>())
                        .add(repoAccess);
            }
        }

        // ── Step 6: Convert intermediateMap into List<UserAccess> ────────────
        // Sort each user's repo list alphabetically
        // Sort the users list alphabetically by username
        List<UserAccess> userAccessList = intermediateMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())   // sort users alphabetically
                .map(entry -> {
                    String username = entry.getKey();
                    List<RepositoryAccess> userRepos = entry.getValue();

                    // sort this user's repos alphabetically
                    userRepos.sort(Comparator.comparing(RepositoryAccess::getRepository));

                    return UserAccess.builder()
                            .username(username)
                            .profileUrl("https://github.com/" + username)
                            .totalRepositories(userRepos.size())
                            .repositories(userRepos)
                            .build();
                })
                .collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - start;
        log.info("Report generated in {}ms — {} users across {} repos", elapsed, userAccessList.size(), repos.size());

        return AccessReport.builder()
                .organization(props.getOrg())
                .generatedAt(Instant.now())
                .totalRepositories(repos.size())
                .totalUsers(userAccessList.size())
                .users(userAccessList)
                .build();
    }
}