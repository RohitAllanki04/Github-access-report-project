# GitHub Organization Access Report Service

A Spring Boot REST API that connects to GitHub and generates a structured JSON report
showing **which users have access to which repositories** — including their exact permission level.

---

## Table of Contents

1. [What This Project Does](#what-this-project-does)
2. [Concepts You Need to Know](#concepts-you-need-to-know)
3. [Project Structure](#project-structure)
4. [How Authentication Works](#how-authentication-works)
5. [Personal Account vs Company Organization](#personal-account-vs-company-organization)
6. [How to Run the Project](#how-to-run-the-project)
7. [How to Call the API](#how-to-call-the-api)
8. [Design Decisions](#design-decisions)
9. [Assumptions](#assumptions)

---

## What This Project Does

GitHub does not have a built-in view that shows "user X has access to repos A, B, C".
You have to check each repo one by one manually.

This service automates that — it:
1. Connects to GitHub using your token
2. Fetches all repositories
3. For each repo, fetches all collaborators and their permission levels
4. Aggregates everything into one clean JSON response: **user → list of repos they can access**

---

## Concepts You Need to Know

### 1. GitHub Personal Access Token (PAT)
A token you generate from your GitHub account to authenticate API calls.
Instead of sending your username/password, you send this token.
It starts with `ghp_`.

### 2. GitHub REST API
GitHub exposes its data via REST API endpoints.
Example: `GET https://api.github.com/user/repos` returns all your repositories.
All API calls require an Authorization header with your token.

### 3. Spring Boot
Java framework that makes building REST APIs easy.
It handles HTTP routing, JSON conversion, dependency injection automatically.

### 4. RestTemplate
Spring's HTTP client — used to make API calls to GitHub from inside the Java code.

### 5. CompletableFuture + @Async (Parallel Processing)
With 100+ repos, fetching collaborators one by one (sequentially) takes 20-40 seconds.
Instead, we fire all collaborator requests **at the same time** (in parallel) using async threads.
This reduces total time to 1-2 seconds.

### 6. Pagination
GitHub returns max 100 items per API call.
If an org has 250 repos, we need 3 API calls (100 + 100 + 50).
Our code loops automatically until all pages are fetched using GitHub's `Link` response header.

### 7. Caffeine Cache
After the first API call, results are stored in memory for 5 minutes.
Second request within 5 minutes returns instantly without calling GitHub again.
This avoids hitting GitHub's rate limit (5000 requests/hour).

### 8. DTO vs Response Model
- **DTO (Data Transfer Object)** — matches GitHub's JSON shape exactly (what GitHub sends us)
- **Response Model** — our own clean JSON shape (what we send to the client)
These are kept separate so GitHub API changes don't break our API contract.

### 9. Access Levels
GitHub has 5 permission levels (highest to lowest):

| Level | Can Do |
|---|---|
| `admin` | Full control, can delete repo |
| `maintain` | Manage repo settings, cannot delete |
| `write` | Push code, merge pull requests |
| `triage` | Manage issues/PRs, cannot push code |
| `read` | View and clone only |

---

## Project Structure

```
github-access-report/
│
├── pom.xml                          → Maven build file, all dependencies
├── .env.example                     → Template for environment variables
├── .gitignore                       → Prevents token/secrets from being committed
│
├── src/main/resources/
│   └── application.yml              → All configuration (reads from env variables)
│
└── src/main/java/com/github/accessreport/
    │
    ├── AccessReportApplication.java → Main entry point, enables Caching + Async
    │
    ├── config/
    │   ├── GitHubProperties.java    → Typed config: token, org, baseUrl, perPage
    │   └── AppConfig.java           → Creates RestTemplate, thread pool, cache beans
    │
    ├── controller/
    │   └── ReportController.java    → HTTP endpoints (thin layer, no business logic)
    │
    ├── service/
    │   ├── GitHubService.java       → All GitHub API calls + pagination + async
    │   └── ReportService.java       → Orchestrates parallel calls + aggregates data
    │
    ├── model/
    │   ├── dto/                     → What GitHub API sends us (input shapes)
    │   │   ├── GitHubRepo.java      → Maps GitHub repo JSON response
    │   │   ├── GitHubUser.java      → Maps GitHub org member JSON response
    │   │   └── GitHubCollaborator.java → Maps collaborator + permissions JSON
    │   │
    │   └── response/                → What our API sends to clients (output shapes)
    │       ├── AccessReport.java    → Top-level response envelope
    │       ├── UserAccess.java      → One user + all their repos
    │       └── RepositoryAccess.java → One repo entry with access level
    │
    └── exception/
        ├── GitHubApiException.java      → Custom exception for GitHub API errors
        └── GlobalExceptionHandler.java  → Converts exceptions to clean JSON errors
```

### Why This Structure?

Each layer has ONE job (Single Responsibility Principle):
- **Controller** — only handles HTTP (URLs, status codes)
- **ReportService** — only aggregates data
- **GitHubService** — only talks to GitHub API
- **Config** — only sets up infrastructure beans
- **Model** — only defines data shapes

---

## How Authentication Works

### Step 1 — You generate a GitHub Personal Access Token
GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token

### Step 2 — Token is stored in application.yml (never in code)
```yaml
github:
  token: ${GITHUB_TOKEN}   # reads from environment variable
```

### Step 3 — Every API call includes the token as a Bearer header
```java
headers.setBearerAuth(props.getToken());
headers.set("Accept", "application/vnd.github+json");
headers.set("X-GitHub-Api-Version", "2022-11-28");
```

### Why Environment Variables?
Hardcoding the token in code is a security risk.
If you push to GitHub, the token is exposed publicly.
GitHub automatically detects and revokes exposed tokens.
Environment variables keep secrets out of source code entirely.

---

## Personal Account vs Company Organization

This is the most important configuration decision.

### Personal Account (for testing)

**Who uses this:** Individual developers testing the project locally

**application.yml:**
```yaml
github:
  token: ghp_yourPersonalToken
  org: your-github-username        # e.g. sujat
  account-type: user
```

**GitHubService.java — fetchAllRepositories():**
```java
// Use /user/repos — returns ALL repos of the authenticated user
// (private + public + forked)
String url = props.getBaseUrl() + "/user/repos";
```

**GitHubService.java — fetchAllOrgMembers():**
```java
// Personal accounts have no org members
// Return empty list
return Collections.emptyList();
```

**Token scopes needed:**
- ✅ `repo` — read your private + public repositories
- ✅ `read:user` — read your profile info

---

### Company Organization (for production / assessment)

**Who uses this:** Company with a GitHub organization like "google", "microsoft", "my-company"

**application.yml:**
```yaml
github:
  token: ${GITHUB_TOKEN}
  org: ${GITHUB_ORG}               # e.g. my-company
  account-type: org
```

**GitHubService.java — fetchAllRepositories():**
```java
// Use /orgs/{org}/repos — returns all repos in the org
String url = props.getBaseUrl() + "/orgs/{org}/repos";
```

**GitHubService.java — fetchAllOrgMembers():**
```java
// Org accounts have real members — fetch them all
String url = props.getBaseUrl() + "/orgs/{org}/members";
```

**Token scopes needed:**
- ✅ `repo` — read all org repositories (private + public)
- ✅ `read:org` — read organization members and teams

---

### Side-by-Side Comparison

| | Personal Account | Company Org |
|---|---|---|
| `account-type` value | `user` | `org` |
| Repos endpoint | `/user/repos` | `/orgs/{org}/repos` |
| Members endpoint | Not used (empty list) | `/orgs/{org}/members` |
| `org` value in yml | your GitHub username | company org slug |
| Token scope | `repo` + `read:user` | `repo` + `read:org` |
| Who creates token | You | Org admin |

**The code is the same — only configuration changes.**

---

## How to Run the Project

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- IntelliJ IDEA (or any IDE)
- A GitHub Personal Access Token

### Step 1 — Clone or download the project
```bash
git clone https://github.com/yourusername/access-report.git
cd access-report
```

### Step 2 — Configure application.yml
Open `src/main/resources/application.yml` and fill in your values:

**For personal testing:**
```yaml
github:
  token: ghp_xxxxxxxxxxxxxxxxxxxx    # your actual token
  org: your-github-username          # your GitHub username
  base-url: https://api.github.com
  per-page: 100
  connect-timeout-ms: 5000
  read-timeout-ms: 10000
```

**For company org:**
```yaml
github:
  token: ghp_companyAdminToken
  org: company-org-name
  base-url: https://api.github.com
  per-page: 100
  connect-timeout-ms: 5000
  read-timeout-ms: 10000
```

**For GitHub Enterprise (different base URL):**
```yaml
github:
  token: ghp_yourToken
  org: company-org-name
  base-url: https://github.your-company.com/api/v3   # override this
```

### Step 3 — Build the project
```bash
mvn clean install
```

### Step 4 — Run the project
```bash
mvn spring-boot:run
```

Or run `AccessReportApplication.java` directly from IntelliJ.

### Step 5 — Verify it started
You should see this in the console:
```
Tomcat started on port 8080
Started AccessReportApplication in X seconds
```

---

## How to Call the API

### Using Postman

**Full Report:**
```
Method : GET
URL    : http://localhost:8080/api/v1/report
Headers: (none needed — token is handled internally)
```

**Single User Report:**
```
Method : GET
URL    : http://localhost:8080/api/v1/report/users/{username}
Example: http://localhost:8080/api/v1/report/users/sujat
```

**Health Check:**
```
Method : GET
URL    : http://localhost:8080/actuator/health
```

### Using curl
```bash
curl http://localhost:8080/api/v1/report
curl http://localhost:8080/api/v1/report/users/sujat
```

### Expected Response

```json
{
  "organization": "sujat",
  "generatedAt": "2024-03-08T05:30:00Z",
  "totalRepositories": 22,
  "totalUsers": 1,
  "users": [
    {
      "username": "sujat",
      "profileUrl": "https://github.com/sujat",
      "totalRepositories": 22,
      "repositories": [
        {
          "repository": "my-project",
          "fullName": "sujat/my-project",
          "accessLevel": "admin",
          "privateRepo": false,
          "repositoryUrl": "https://github.com/sujat/my-project"
        },
        {
          "repository": "another-repo",
          "fullName": "sujat/another-repo",
          "accessLevel": "admin",
          "privateRepo": true,
          "repositoryUrl": "https://github.com/sujat/another-repo"
        }
      ]
    }
  ]
}
```

### Error Responses

**401 — Bad token:**
```json
{
  "error": "GitHub API Error",
  "message": "GitHub token is invalid or expired.",
  "timestamp": "2024-03-08T05:30:00Z"
}
```

**403 — Wrong scopes:**
```json
{
  "error": "GitHub API Error",
  "message": "Permission denied. Ensure token has repo and read:org scopes.",
  "timestamp": "2024-03-08T05:30:00Z"
}
```

---

## Design Decisions

### 1. Parallel API Calls (Most Important)
**Problem:** With 100 repos, fetching collaborators sequentially = 20 seconds minimum.
**Solution:** Fire all collaborator requests simultaneously using `@Async` + `CompletableFuture`.
**Result:** Total time drops to 1-2 seconds regardless of repo count.

```
Sequential:  [repo1] → [repo2] → [repo3] → ... → [repo100]  = 20s
Parallel:    [repo1]
             [repo2]
             [repo3]   all at same time                       = 1-2s
             [repo100]
```

### 2. Caching
Results cached for 5 minutes using Caffeine.
Avoids hitting GitHub's rate limit (5000 requests/hour).
Second request within 5 minutes returns instantly.

### 3. Data Inversion
GitHub gives: `repo → [list of users]`
We return: `user → [list of repos]`
This inversion happens in ReportService using `computeIfAbsent`.

### 4. Bot Exclusion
GitHub bots like `dependabot[bot]` and `github-actions[bot]` are automatically
excluded from the report. They are service accounts, not real people.

### 5. Separation of Concerns
`GitHubService` — knows HOW to call GitHub API
`ReportService` — knows HOW to aggregate the data
These are intentionally separate so each class has one job.

### 6. Global Error Handling
All exceptions are caught by `GlobalExceptionHandler`.
Clients always receive clean JSON errors — never raw stack traces.
401 → 502 Bad Gateway. 500 → Internal Server Error.

### 7. Token Security
Token is never hardcoded.
Always injected via environment variable `${GITHUB_TOKEN}`.
`application.yml` should be added to `.gitignore` when token is hardcoded for testing.

---

## Assumptions

1. **Token has sufficient permissions** — `repo` and `read:org` scopes are required for full functionality.

2. **Bots are excluded** — GitHub bot accounts are filtered out as they are not human users.

3. **Personal vs Org mode** — The endpoint used differs between personal accounts (`/user/repos`) and organizations (`/orgs/{org}/repos`). The README documents both.

4. **Cache TTL of 5 minutes** — Acceptable for access reports. Real-time accuracy is less critical than staying within rate limits.

5. **100 items per page** — GitHub's maximum. Our pagination loop handles any number of repos/users automatically.

6. **Thread pool of 20** — Balances speed and GitHub rate limit safety. Configurable in `AppConfig.java` if needed.

7. **Outside collaborators included** — `affiliation=all` in the collaborators API includes direct collaborators, team members, and outside collaborators.
