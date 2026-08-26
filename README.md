---

# Code Auditor & Agent Orchestration Pipeline

An autonomous **Tech Lead & Code Auditor Agent** built in **Java 21** and **Spring Boot 3.x**. This system acts as a local orchestrator that continuously audits repositories, inspects Firebase Crashlytics logs, monitors GitHub Actions CI/CD pipelines, and identifies code duplications or architectural debt.

Instead of modifying project code directly, it uses **Spec-Driven Design (SDD)** to analyze problems, create structured **GitHub Issues**, and delegate execution tasks to specialized AI agents such as **Google Jules** (for backend/code logic) and **Google Stitch** (for UI/UX material design refactoring).

---

## 🏗️ Architecture & Operational Workflow

```
[Firebase Crashlytics] ────┐
[GitHub Actions CI/CD] ────┼──► [Agent 1: Triage & Audit] ──► Creates GitHub Issues (SDD)
[AST Duplicate Checker] ───┘             │
                                         ▼
                                [Agent 2: Delegation]
                                  ├──► Google Stitch (UI Refactoring + Material Theme)
                                  └──► Google Jules API ──► Opens Fix Pull Requests (PR)

```

### 1. Agent 1: Triage, Detection & Spec-Driven Design

* **Log & Crash Inspection:** Connects to Firebase Crashlytics to monitor unresolved exceptions on the latest deployed releases.
* **CI/CD Monitoring:** Listens to GitHub Actions pipeline failures (`Linter`, `Unit/Integration Tests`, `Build steps`).
* **Deduplication Check:** Queries existing open/closed GitHub issues to avoid duplicate reports.
* **Triage Engine:** Evaluates whether an issue is deterministically fixable by an agent or requires human developer intervention.
* **Issue Generation:** Writes a structured **Spec-Driven Design (SDD)** checklist and publishes a GitHub Issue with a suggested branch name (`fix/xxxx` or `refactor/xxxx`).

### 2. Agent 2: Resolution & Agentic Delegation

* **Code Resolution:** Reads the GitHub Issue and delegates execution directly to **Google Jules API**.
* **UI/UX Optimization:** Converts component AST structures into Google Stitch DSL format, injects the project's `MaterialTheme` design tokens, verifies the Stitch output, and passes it to Google Jules for PR generation.

---

## 🛠️ Tech Stack & Prerequisites

* **Java 21** & **Spring Boot 3.4+**
* **LangChain4j:** LLM orchestration supporting **LM Studio (local)**, Google Gemini, Anthropic Claude, and OpenAI.
* **GitHub REST & GraphQL API:** For issue tracking, actions log analysis, and PR monitoring.
* **Firebase Admin SDK:** For Crashlytics event retrieval.

---

## ⚙️ Configuration Files

### 1. Model Provider Configuration (`agent.yml`)

The agent supports switching between local models (LM Studio, Docker AI) and cloud LLM providers via the `active_profile` property.

```yaml
agent:
  name: "CodeAuditorAgent"
  version: "1.0.0"

active_profile: "local_lmstudio"

profiles:
  local_lmstudio:
    provider: "lmstudio"
    base_url: "http://localhost:1234/v1"
    model: "qwen2.5-coder-32b-instruct"
    temperature: 0.2

  cloud_gemini:
    provider: "google"
    api_key_env: "GEMINI_API_KEY"
    model: "gemini-1.5-pro"

  cloud_anthropic:
    provider: "anthropic"
    api_key_env: "ANTHROPIC_API_KEY"
    model: "claude-3-5-sonnet-20241022"

```

### 2. Monitored Repositories (`repositories.yaml`)

Define target projects, Firebase settings, and GitHub Actions preferences:

```yaml
global_settings:
  check_interval_minutes: 30
  auto_triage_enabled: true

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
    crashlytics:
      enabled: true
      app_id: "1:1234567890:android:abcdef123456"
      min_error_threshold: 3
    stitch:
      enabled: true
      theme_config_path: "src/theme/material-theme.json"

```

---

## 🔑 Environment Variables Setup (`.env`)

Create a local `.env` file in the project root:

```env
# GitHub Credentials
GITHUB_PERSONAL_ACCESS_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx

# Firebase & Google Developer APIs
FIREBASE_SERVICE_ACCOUNT_KEY_PATH="./config/firebase-service-account.json"
GOOGLE_JULES_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXX
GOOGLE_STITCH_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXX

# LLM Providers (Required based on active_profile)
GEMINI_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXX
ANTHROPIC_API_KEY=sk-ant-api03-xxxxxxxx

```

---

## 🚀 Running the Orchestrator Locally

1. **Clone the Repository:**
```bash
git clone https://github.com/your-org/code-auditor-agent.git
cd code-auditor-agent

```


2. **Verify Local LM Studio (Optional):**
Ensure **LM Studio** is running locally on port `1234` if using the default profile.
3. **Build & Run Application:**
```bash
./mvnw clean package
java -jar target/code-auditor-agent-1.0.0.jar

```



---

## 📋 License

This project is proprietary and intended for internal development workflow automation.
