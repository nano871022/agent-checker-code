# Detailed Project Specification: Autonomous Code Auditor & Agent Orchestrator

## 1. Executive Summary & Vision

This project defines an autonomous, locally hosted **Tech Lead & Code Auditor Agent** built using **Java 21** and **Spring Boot 3.4+**. 

The agent operates as a continuous background daemon executing a **Sequential Round-Robin Loop** over a list of registered software repositories. It acts as a proactive quality manager by inspecting **Firebase Crashlytics** logs for production crashes, monitoring **GitHub Actions CI/CD** pipeline failures (linter, tests, build breaks), and performing **AST-based Code Duplication Analysis** (DRY principle enforcement).

### Key Architectural Philosophy
* **Non-Invasive Analysis (No Direct Code Mutation):** The agent never edits repository code directly. Instead, it acts as an **Orchestrator and Auditor**.
* **Spec-Driven Design (SDD):** When an issue or code smell is identified, the agent generates a structured, step-by-step task checklist and publishes a **GitHub Issue**.
* **Agentic Delegation:** Execution is delegated to specialized downstream agents:
  * **Google Jules API:** Receives code-level fixes, implements changes, and opens a Pull Request (PR).
  * **Google Stitch API:** Receives UI/UX refactoring tasks along with design tokens (`MaterialTheme`) and transformed AST representations, validates the UI output, and delegates implementation to Google Jules.

---

## 2. High-Level Architecture & Workflow


```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                LOCAL DEVELOPER MACHINE                                 │
│                                                                                        │
│  ┌───────────────────────┐   ┌───────────────────────────┐   ┌──────────────────────┐  │
│  │   repositories.yaml   │   │         agent.yml         │   │      .env File       │  │
│  └───────────┬───────────┘   └─────────────┬─────────────┘   └──────────┬───────────┘  │
│              │                             │                            │              │
│              ▼                             ▼                            ▼              │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                    SPRING BOOT 3.4+ DAEMON ORCHESTRATOR                          │  │
│  │                                                                                  │  │
│  │  ┌────────────────────────┐  ┌─────────────────────────┐  ┌────────────────────┐ │  │
│  │  │ RepositoryQueueManager │  │  TriageService (Agent 1)│  │ LangChain4j Engine │ │  │
│  │  └───────────┬────────────┘  └────────────┬────────────┘  └──────────┬─────────┘ │  │
│  └──────────────┼────────────────────────────┼──────────────────────────┼───────────┘  │
└─────────────────┼────────────────────────────┼──────────────────────────┼──────────────┘
│                            │                          │
▼                            ▼                          ▼
┌──────────────────────────┐  ┌──────────────────────────┐  ┌──────────────────────┐
│  Firebase Crashlytics    │  │   GitHub REST/GraphQL    │  │  Local LM Studio /   │
│  (Crash Log Extraction)  │  │   (Issues, CI/CD Logs)   │  │  Cloud LLM Providers │
└──────────────────────────┘  └────────────┬─────────────┘  └──────────────────────┘
│
▼
┌──────────────────────────┐
│   Google Jules API &     │
│   Google Stitch API      │
│   (PR Creation / UI)     │
└──────────────────────────┘

```

---

## 3. Component Deep Dive

### 3.1. Engine & Runtime (Java 21 + Spring Boot 3.4)
* **Java 21 LTS:** Chosen for its performance, structured concurrency, virtual threads (Project Loom) for lightweight I/O bound HTTP interactions, and modern language features (Records, Pattern Matching, Sealed Classes).
* **Spring Boot 3.4+:** Provides the enterprise foundation (`RestClient`, `@Scheduled` tasks, `@ConfigurationProperties`, `@RestController` for queue management).
* **LangChain4j (`v0.35.0+`):** Unified framework for LLM interaction. Handles chat memory, prompt templating, structured JSON responses, and provider switching.

### 3.2. Agent 1: Triage, Detection & Spec-Driven Design Engine
* **Firebase Crashlytics Inspector:** Periodically queries unresolved crash events for the active app version. Extracts exception types, root stack traces, and affected internal package paths.
* **GitHub Actions CI/CD Inspector:** Intercepts failed workflow runs (`GET /repos/{owner}/{repo}/actions/runs`). Downloads raw job logs to isolate linter failures (e.g., ESLint, Ktlint, Checkstyle) or unit/integration test assertions.
* **AST Code Duplication Inspector:** Scans project source files using AST parsing (`JavaParser` / `Tree-sitter`). Identifies duplicate logic blocks and flags them for central refactoring under the DRY principle.
* **Deduplication Engine:** Queries open and closed GitHub issues prior to posting (`is:issue label:agent-autofix <fingerprint>`) to prevent duplicate task generation.
* **Triage Classifier:**
  * **Automated Agent Candidate (`RESOLVABLE_BY_AGENT`):** Deterministic errors (`NullPointerException`, `IndexOutOfBoundsException`, syntax/linter rules, isolated unit test failures).
  * **Human Escalation (`REQUIRES_HUMAN_INTERVENTION`):** Infrastructure failures, `OutOfMemoryError`, security/credential updates, closed-source third-party dependencies, or missing hardware capabilities.

### 3.3. Agent 2: Delegation & Integration Engine
* **Google Jules Integration:** Receives the issue context, suggested branch name (`fix/xxxx`), and task checklist. Invokes Jules API to write code, execute tests, and open a PR.
* **Google Stitch UI Integration:**
  1. Extracts component UI source code from the repository.
  2. Applies an AST transformer to convert code into a Stitch-compatible DSL.
  3. Injects design system tokens (`material-theme.json`).
  4. Submits payload to Google Stitch API.
  5. Validates that the generated layout matches requirements before passing code instructions to Google Jules for final PR creation.

---

## 4. Configuration Schema Specifications

### 4.1. LLM Profile Configuration (`agent.yml`)
Located at project root or application config directory.

```yaml
agent:
  name: "CodeAuditorAgent"
  version: "1.0.0"

# Active profile name pointing to one of the keys in 'profiles'
active_profile: "local_lmstudio"

profiles:
  local_lmstudio:
    provider: "lmstudio"
    base_url: "http://localhost:1234/v1"
    model: "qwen2.5-coder-32b-instruct"
    temperature: 0.2
    context_window: 32768

  docker_ai:
    provider: "docker_ai"
    base_url: "http://localhost:11434"
    model: "codellama:latest"

  cloud_gemini:
    provider: "google"
    api_key_env: "GEMINI_API_KEY"
    model: "gemini-1.5-pro"

  cloud_anthropic:
    provider: "anthropic"
    api_key_env: "ANTHROPIC_API_KEY"
    model: "claude-3-5-sonnet-20241022"

```

### 4.2. Repository Registry Configuration (`repositories.yaml`)

Defines all repositories monitored by the daemon loop.

```yaml
global_settings:
  check_interval_minutes: 30
  auto_triage_enabled: true
  state_file_path: "./.agent-state.json"

repositories:
  - name: "mobile-app-service"
    enabled: true
    package_name: "com.company.app"
    github:
      owner: "your-organization"
      repo: "mobile-app"
      default_branch: "main"
      issue_labels: ["agent-autofix", "crashlytics"]
    github_actions:
      enabled: true
      workflows_to_monitor: ["ci.yml", "lint-and-test.yml"]
      auto_fix_lint: true
      auto_fix_tests: true
    crashlytics:
      enabled: true
      app_id: "1:1234567890:android:abcdef123456"
      package_name: "com.company.app"
      min_error_threshold: 3
    stitch:
      enabled: true
      theme_config_path: "src/theme/material-theme.json"
      ui_components_dir: "src/components/ui"

  - name: "backend-api-service"
    enabled: true
    package_name: "com.company.backend"
    github:
      owner: "your-organization"
      repo: "backend-api"
      default_branch: "develop"
      issue_labels: ["agent-autofix", "backend"]
    github_actions:
      enabled: true
      workflows_to_monitor: ["build-and-test.yml"]
      auto_fix_lint: true
      auto_fix_tests: true
    crashlytics:
      enabled: false
    stitch:
      enabled: false

```

### 4.3. Environment Variables (`.env`)

```env
# GitHub API Access
GITHUB_PERSONAL_ACCESS_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Firebase & Google Developer Credentials
FIREBASE_SERVICE_ACCOUNT_KEY_PATH="./config/firebase-service-account.json"
GOOGLE_JULES_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
GOOGLE_STITCH_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

# Cloud LLM Provider API Keys (Used when active_profile selects them)
GEMINI_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
ANTHROPIC_API_KEY=sk-ant-api03-xxxxxxxxxxxxxxxxxxxxxxxx

```

---

## 5. Sequential Execution & Priority Queue Algorithm

The orchestrator executes a sequential round-robin loop. Only one repository is processed at any given time to prevent resource contention and API rate-limiting.

```
┌─────────────────────────────────────────────────────────────┐
│               Sequential Loop Daemon Workflow               │
└─────────────────────────────────────────────────────────────┘
                               │
       [Read Next Repository Config from Queue File]
                               │
                               ▼
     ┌───────────────────────────────────────────────────┐
     │  Is there a Priority Override Request in Queue?  │
     └───────────────────────────────────────────────────┘
                │                               │
             YES│                               │NO
                ▼                               ▼
     [Pop Priority Repository]       [Pop Normal Queue Head]
                │                               │
                └───────────────┬───────────────┘
                                ▼
     ┌───────────────────────────────────────────────────┐
     │           Execute Complete Triage Pipeline        │
     │  1. Check Firebase Crashlytics                    │
     │  2. Check GitHub Actions Logs                     │
     │  3. Check AST Code Duplications                   │
     └───────────────────────────────────────────────────┘
                                │
                                ▼
     ┌───────────────────────────────────────────────────┐
     │        Found Resolvable Issues/Crashes?           │
     └───────────────────────────────────────────────────┘
                │                               │
             YES│                               │NO
                ▼                               │
     [Build SDD Issue & Post to GitHub]         │
                │                               │
                ▼                               │
     [Delegate to Jules / Stitch API]           │
                │                               │
                └───────────────┬───────────────┘
                                ▼
      [Update State File (.agent-state.json)]
                                │
                                ▼
      [Sleep for configured check_interval_minutes]
                                │
                                ▼
      [Advance to Next Repository in Queue]

```

### Manual Priority Override Endpoint

A REST controller allows forcing immediate execution of a specific repository without restarting the daemon:

* **Endpoint:** `POST /api/v1/queue/prioritize`
* **Request Payload:**
```json
{
  "repository_name": "mobile-app-service",
  "force_immediate": true
}

```


* **Behavior:** Pushes the designated repository to the head of the thread-safe `ConcurrentLinkedDeque`. The agent completes its current atomic step, audits the prioritized repository immediately, and then resumes normal round-robin order.

---

## 6. Detailed Data Schemas & Templates

### 6.1. GitHub Issue Markdown Template (Agent 1 Output for SDD)

When Agent 1 flags a crash or CI failure, it writes a structured Markdown issue:

```markdown
## [Crashlytics][v1.2.4] - NullPointerException in UserProfileAdapter.kt

### 1. Overview
Automated crash report captured from Firebase Crashlytics for version `v1.2.4`.

- **Repository:** `your-organization/mobile-app`
- **Target Branch:** `main`
- **Suggested Fix Branch:** `fix/crash-v1.2.4-a8f3b2`
- **Exception Type:** `java.lang.NullPointerException`
- **Affected File:** `src/main/java/com/company/app/ui/UserProfileAdapter.kt`
- **Line Number:** `84`

---

### 2. Stack Trace
```text
java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String com.company.app.models.User.getName()' on a null object reference
    at com.company.app.ui.adapters.UserProfileAdapter.onBindViewHolder(UserProfileAdapter.kt:84)
    at androidx.recyclerview.widget.RecyclerView$Adapter.bindViewHolder(RecyclerView.java:7107)
```

---

### 3. Spec-Driven Design Task Checklist (For Google Jules)
- [ ] Locate `UserProfileAdapter.kt` at line 84.
- [ ] Inspect the `User` object extraction logic inside `onBindViewHolder`.
- [ ] Implement a safe-call operator or explicit null verification before calling `getName()`.
- [ ] Provide a default fallback string resource (e.g., `"Unknown User"`) when object is null.
- [ ] Execute existing unit tests (`./gradlew test` or `./mvnw test`) to verify fix integrity.
- [ ] Open a Pull Request pointing to `main` with title matching the issue header.

```

### 6.2. Google Stitch UI Integration Payload Schema (JSON)

When an issue involves UI refactoring, Agent 2 constructs this payload for Google Stitch:

```json
{
  "project_name": "mobile-app-service",
  "component_name": "UserProfileHeader",
  "source_code_ast": {
    "type": "UIComponent",
    "framework": "AndroidCompose",
    "raw_code": "@Composable fun UserProfileHeader(user: User?) { Text(text = user.name) }"
  },
  "design_system": {
    "material_version": 3,
    "theme_tokens": {
      "color_primary": "#6200EE",
      "color_secondary": "#03DAC6",
      "font_family": "Roboto",
      "border_radius_dp": 8
    }
  },
  "instructions": "Refactor component to handle null user state gracefully, apply primary color token to header, and ensure compliance with Material 3 spacing rules."
}

```

---

## 7. Java Core Implementation Reference

### 7.1. LangChain4j AI Service Interface (`CrashAnalysisAiService.java`)

```java
package com.agent.orchestrator.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CrashAnalysisAiService {

    @SystemMessage("""
        You are a Principal Tech Lead evaluating automated code fix viability.
        Analyze the provided application crash report, stack trace, and code context.
        Respond with 'AGENT' ONLY if the issue can be resolved purely by modifying internal application code without infrastructure, credential, or external library changes.
        Respond with 'HUMAN' in all other cases.
        """)
    @UserMessage("""
        Crash Exception: {{exceptionType}}
        Error Message: {{errorMessage}}
        Stack Trace:
        {{stackTrace}}
        """)
    String evaluateFixViability(String exceptionType, String errorMessage, String stackTrace);
}

```

### 7.2. Triage Logic Implementation (`CrashTriageService.java`)

```java
package com.agent.orchestrator.service;

import com.agent.orchestrator.model.CrashEvent;
import com.agent.orchestrator.model.RepoConfig;
import com.agent.orchestrator.model.TriageDecision;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrashTriageService {

    private final CrashAnalysisAiService aiService;
    private final GitHubClientService gitHubService;

    private static final List<String> AGENT_RESOLVABLE_EXCEPTIONS = List.of(
        "NullPointerException", "KotlinNullPointerException",
        "IndexOutOfBoundsException", "ClassCastException",
        "IllegalArgumentException", "JsonParseException",
        "UninitializedPropertyAccessException"
    );

    private static final List<String> HUMAN_REQUIRED_EXCEPTIONS = List.of(
        "OutOfMemoryError", "StackOverflowError",
        "SecurityException", "SSLException", "SQLiteFullException"
    );

    public CrashTriageService(CrashAnalysisAiService aiService, GitHubClientService gitHubService) {
        this.aiService = aiService;
        this.gitHubService = gitHubService;
    }

    public TriageDecision evaluateCrash(CrashEvent crash, RepoConfig config) {
        // 1. Deduplication check on GitHub Issues
        if (gitHubService.doesIssueExist(crash.getFingerprint(), crash.getAppVersion())) {
            return TriageDecision.IGNORE_DUPLICATE;
        }

        // 2. Verify that affected frame belongs to the application package
        if (!crash.getPrimaryFrame().getPackageName().startsWith(config.getPackageName())) {
            return TriageDecision.REQUIRES_HUMAN_INTERVENTION;
        }

        String exceptionType = crash.getExceptionType();

        // 3. Rule-based exception classification
        if (HUMAN_REQUIRED_EXCEPTIONS.contains(exceptionType)) {
            return TriageDecision.REQUIRES_HUMAN_INTERVENTION;
        }

        if (AGENT_RESOLVABLE_EXCEPTIONS.contains(exceptionType)) {
            return TriageDecision.RESOLVABLE_BY_AGENT;
        }

        // 4. Fallback LLM Semantic Evaluation for ambiguous stack traces
        String aiResponse = aiService.evaluateFixViability(
            exceptionType,
            crash.getMessage(),
            crash.getStackTrace()
        );

        return "AGENT".equalsIgnoreCase(aiResponse.trim())
                ? TriageDecision.RESOLVABLE_BY_AGENT
                : TriageDecision.REQUIRES_HUMAN_INTERVENTION;
    }
}

```

---

## 8. Definition of Done (DoD) & Verification Matrix

| Component | Success Criteria | Verification Method |
| --- | --- | --- |
| **Configuration Engine** | Correctly parses `agent.yml` and `repositories.yaml` at startup. | Spring Boot startup test / Integration tests. |
| **LM Provider Switching** | Seamlessly connects to local LM Studio (`localhost:1234`) or Cloud APIs based on `active_profile`. | LangChain4j unit integration tests. |
| **Crashlytics Pipeline** | Fetches unresolved crashes and filters out non-app package frames. | Firebase Admin SDK mock tests. |
| **CI/CD Failure Pipeline** | Downloads GitHub Actions logs and isolates Linter/Test failures. | GitHub API WireMock tests. |
| **Issue Deduplication** | Never posts duplicate GitHub issues for an active fingerprint. | GitHub Search API query validation. |
| **Priority Queue Engine** | REST API `POST /api/v1/queue/prioritize` immediately preempts execution order. | Multi-threaded Integration Test. |
| **Agent Delegation** | Successfully posts payload to Jules/Stitch APIs with SDD task checklist in English. | Mocked WebClient integration suite. |

```

```
