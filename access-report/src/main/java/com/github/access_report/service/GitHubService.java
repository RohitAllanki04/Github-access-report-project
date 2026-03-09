package com.github.access_report.service;

import com.github.access_report.config.GitHubProperties;
import com.github.access_report.exception.GitHubApiException;
import com.github.access_report.model.dto.GitHubCollaborator;
import com.github.access_report.model.dto.GitHubRepo;
import com.github.access_report.model.dto.GitHubUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final RestTemplate restTemplate;
    private final GitHubProperties props;

    // ─────────────────────────────────────────────────────────────
    // 1. Fetch all repositories
    // ─────────────────────────────────────────────────────────────

    @Cacheable(value = "repositories", key = "'all-repos'")
    public List<GitHubRepo> fetchAllRepositories() {
        log.info("Fetching all repositories for user/org: {}", props.getOrg());

        String url = props.getBaseUrl() + "/users/{org}/repos";
        List<GitHubRepo> all = new ArrayList<>();
        int page = 1;

        while (true) {
            String pagedUrl = UriComponentsBuilder.fromUriString(url)
                    .queryParam("type", "all")
                    .queryParam("per_page", props.getPerPage())
                    .queryParam("page", page)
                    .buildAndExpand(props.getOrg())
                    .toUriString();

            ResponseEntity<GitHubRepo[]> response = callGitHub(pagedUrl, GitHubRepo[].class);

            GitHubRepo[] body = response.getBody();
            if (body == null || body.length == 0) break;

            all.addAll(Arrays.asList(body));
            log.debug("Repos — page {}, total so far: {}", page, all.size());

            if (!hasNextPage(response.getHeaders())) break;
            page++;
        }

        log.info("Total repositories fetched: {}", all.size());
        return all;
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Fetch all org members
    // ─────────────────────────────────────────────────────────────

    @Cacheable(value = "orgMembers", key = "'all-members'")
    public List<GitHubUser> fetchAllOrgMembers() {
        // For personal accounts, just return empty list
        return Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Fetch collaborators per repo (ASYNC — runs in parallel)
    // ─────────────────────────────────────────────────────────────

    @Async("githubTaskExecutor")
    @Cacheable(value = "collaborators", key = "#repoName")
    public CompletableFuture<List<GitHubCollaborator>> fetchCollaboratorsAsync(String repoName) {
        log.debug("Fetching collaborators for repo: {}", repoName);

        String url = props.getBaseUrl() + "/repos/{org}/{repo}/collaborators";
        List<GitHubCollaborator> all = new ArrayList<>();
        int page = 1;

        try {
            while (true) {
                String pagedUrl = UriComponentsBuilder.fromUriString(url)
                        .queryParam("affiliation", "all")
                        .queryParam("per_page", props.getPerPage())
                        .queryParam("page", page)
                        .buildAndExpand(props.getOrg(), repoName)
                        .toUriString();

                ResponseEntity<GitHubCollaborator[]> response =
                        callGitHub(pagedUrl, GitHubCollaborator[].class);

                GitHubCollaborator[] body = response.getBody();
                if (body == null || body.length == 0) break;

                all.addAll(Arrays.asList(body));

                if (!hasNextPage(response.getHeaders())) break;
                page++;
            }
        } catch (GitHubApiException e) {
            log.warn("Skipping repo '{}' — {}", repoName, e.getMessage());
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.completedFuture(all);
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private <T> ResponseEntity<T> callGitHub(String url, Class<T> responseType) {
        try {
            return restTemplate.exchange(url, HttpMethod.GET, buildRequest(), responseType);

        } catch (HttpClientErrorException.Unauthorized e) {
            throw new GitHubApiException(
                    "GitHub token is invalid or expired. Check your token in application.yml", e);

        } catch (HttpClientErrorException.Forbidden e) {
            throw new GitHubApiException(
                    "Permission denied. Ensure token has 'repo' scope.", e);

        } catch (HttpClientErrorException.NotFound e) {
            throw new GitHubApiException("Resource not found: " + url, e);

        } catch (HttpClientErrorException e) {
            throw new GitHubApiException(
                    "GitHub API error [" + e.getStatusCode().value() + "]: " + e.getMessage(), e);
        }
    }

    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return new HttpEntity<>(headers);
    }

    private boolean hasNextPage(HttpHeaders headers) {
        List<String> linkHeader = headers.get("Link");
        if (linkHeader == null || linkHeader.isEmpty()) return false;
        return linkHeader.get(0).contains("rel=\"next\"");
    }
}