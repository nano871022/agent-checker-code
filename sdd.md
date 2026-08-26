---

# [SPEC-001] Code Auditor & Agent Orchestrator Core Setup

## 🎯 Executive Summary

Build a local autonomous **Tech Lead & Code Auditor Agent** using **Java 21** and **Spring Boot 3.4+**. The system operates as a continuous background daemon executing a sequential round-robin loop across configured repositories. It triages **Firebase Crashlytics** logs, **GitHub Actions CI/CD** failures, and **AST Code Duplications**. Instead of applying code fixes directly, it creates structured GitHub Issues and delegates implementation tasks to **Google Jules API** (for logic/code) and **Google Stitch API** (for UI/UX refactoring).

---

## 🏗️ Technical Architecture & Stack

* **Language/SDK:** Java 21 (LTS)
* **Framework:** Spring Boot 3.4+ (Web, WebClient, Scheduling)
* **Agentic Orchestration:** LangChain4j (`langchain4j-open-ai-spring-boot-starter`)
* **Target Environment:** Local Developer Machine (macOS/Linux/Windows)
* **Integrations:** GitHub REST/GraphQL API, Firebase Admin SDK, Google Jules API, Google Stitch API

---

## 📋 Spec-Driven Design: Task Breakdown

Assign these tasks sequentially to **Google Jules**. Jules must process tasks in order and open atomic PRs linked to each checklist item.

### Phase 1: Project Initialization & Configuration Engine

* [ ] **Task 1.1: Initialize Spring Boot 3.4 Project with Java 21**
* Create a Maven project structure (`pom.xml`) containing:
* `spring-boot-starter-web`
* `spring-boot-starter-validation`
* `langchain4j-open-ai-spring-boot-starter` (v0.35.0+)
* `jackson-dataformat-yaml`
* `lombok` (optional/standard)


* Set Java version to `21` in `pom.xml`.


* [ ] **Task 1.2: Implement Configuration Mappers (`agent.yml` & `repositories.yaml`)**
* Create POJOs / Records to map `agent.yml` (active profile, LLM provider, base URLs, API keys).
* Create POJOs / Records to map `repositories.yaml` (monitored repositories list, check intervals, Crashlytics & Stitch flags).
* Implement `@ConfigurationProperties` classes in Spring Boot to load these files dynamically at startup.


* [ ] **Task 1.3: Build Dynamic LLM Factory with LangChain4j**
* Implement `LlmFactoryConfig.java` to instantiate a `ChatLanguageModel` bean based on `active_profile` in `agent.yml`.
* Ensure compatibility with **LM Studio** local endpoint (`http://localhost:1234/v1`) using OpenAI-compatible specification, with fallback support for Google Gemini and Anthropic Claude keys.



---

### Phase 2: Sequential Loop Engine & Priority Queue

* [ ] **Task 2.1: Implement Round-Robin Repository Queue Manager**
* Create `RepositoryQueueManager.java` managing a thread-safe `ConcurrentLinkedQueue<RepoConfig>` initialized from `repositories.yaml`.
* Add state tracking to persist the last processed repository index in `.agent-state.json`.


* [ ] **Task 2.2: Implement Priority Override Endpoint (`QueueController.java`)**
* Create a REST controller with `POST /api/v1/queue/prioritize`.
* Request Body: `{"repository_name": "string", "force_immediate": boolean}`.
* Implement thread-safe preemption logic to push the requested repository to the head of the execution queue.


* [ ] **Task 2.3: Build Main Orchestration Daemon (`AgentLoopDaemon.java`)**
* Implement a Spring `@Scheduled` background worker running the analysis loop.
* Logic: Fetch next repo -> Execute Triage Pipeline -> Delegate API calls -> Update state -> Sleep for `check_interval_minutes` -> Repeat.



---

### Phase 3: Agent 1 - Triage & Issue Generation Engine

* [ ] **Task 3.1: Build GitHub Integration Service (`GitHubClientService.java`)**
* Create service using `RestClient` or `WebClient` to interact with GitHub REST API:
* Query open/closed issues (`GET /repos/{owner}/{repo}/issues`) to prevent duplicate reports.
* Fetch GitHub Actions run logs (`GET /repos/{owner}/{repo}/actions/runs`).
* Create structured issues (`POST /repos/{owner}/{repo}/issues`).




* [ ] **Task 3.2: Build Firebase Crashlytics Inspection Service**
* Implement `FirebaseCrashlyticsService.java` integrating Firebase Admin SDK.
* Fetch unresolved crash events for the latest release version filtering by minimum error threshold.


* [ ] **Task 3.3: Implement Triage Engine & LangChain4j AI Service**
* Create `CrashAnalysisAiService.java` interface using `@AiService` and `@SystemMessage` in English.
* Implement `TriageService.java` with rule-based filtering (e.g., checking internal package boundaries, deterministic exception types) and LLM fallback evaluation (`TriageDecision`: `RESOLVABLE_BY_AGENT`, `REQUIRES_HUMAN_INTERVENTION`, `IGNORE_DUPLICATE`).


* [ ] **Task 3.4: Implement Markdown Issue Generator (SDD Format)**
* Create `IssueTemplateBuilder.java` to generate English GitHub Issue bodies containing problem overview, stack trace, affected lines, and a step-by-step task checklist for Jules.



---

### Phase 4: Agent 2 - Delegation & API Connectors

* [ ] **Task 4.1: Build Google Jules API Client (`JulesApiClient.java`)**
* Implement HTTP client to delegate created GitHub Issues to Google Jules API.
* Include parameters: Repository URL, Issue ID, and Target Branch Name (`fix/crash-vX.Y.Z-hash`).


* [ ] **Task 4.2: Build Google Stitch UI Transformer (`StitchApiClient.java`)**
* Implement client to send UI code + `material-theme.json` tokens to Google Stitch API.
* Add validation step to verify Stitch response before forwarding modified UI components to Jules for PR creation.



---

## 🧪 Definition of Done (DoD)

1. All unit and integration tests pass via `./mvnw clean test`.
2. Spring Boot application boots successfully with `active_profile: local_lmstudio`.
3. Priority REST endpoint successfully preempts the queue order.
4. Generated GitHub Issues strictly follow English language constraints and SDD task list formatting.
