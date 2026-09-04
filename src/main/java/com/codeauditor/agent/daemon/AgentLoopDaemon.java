package com.codeauditor.agent.daemon;

import com.codeauditor.agent.config.RepositoriesConfigProperties;
import com.codeauditor.agent.config.RepositoriesConfigProperties.RepositoryConfig;
import com.codeauditor.agent.crashlytics.FirebaseCrashlyticsService;
import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.codeauditor.agent.github.GitHubClientService;
import com.codeauditor.agent.github.dto.CreateIssueRequest;
import com.codeauditor.agent.github.dto.GitHubIssue;
import com.codeauditor.agent.github.dto.GitHubContent;
import com.codeauditor.agent.github.dto.GitHubWorkflowRun;
import com.codeauditor.agent.github.dto.GitHubWorkflowRunsResponse;
import com.codeauditor.agent.jules.JulesApiClient;
import com.codeauditor.agent.queue.RepositoryQueueManager;
import com.codeauditor.agent.triage.IssueTemplateBuilder;
import com.codeauditor.agent.triage.TriageService;
import com.codeauditor.agent.triage.dto.TriageDecision;
import com.codeauditor.agent.triage.dto.TriageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AgentLoopDaemon {

    private final RepositoryQueueManager queueManager;
    private final RepositoriesConfigProperties repositoriesConfig;
    private final FirebaseCrashlyticsService crashlyticsService;
    private final GitHubClientService gitHubClientService;
    private final TriageService triageService;
    private final IssueTemplateBuilder issueTemplateBuilder;
    private final JulesApiClient julesApiClient;

    public AgentLoopDaemon(RepositoryQueueManager queueManager, RepositoriesConfigProperties repositoriesConfig) {
        this(queueManager, repositoriesConfig, null, null, null, null, null);
    }

    @Autowired
    public AgentLoopDaemon(
            RepositoryQueueManager queueManager,
            RepositoriesConfigProperties repositoriesConfig,
            FirebaseCrashlyticsService crashlyticsService,
            GitHubClientService gitHubClientService,
            TriageService triageService,
            IssueTemplateBuilder issueTemplateBuilder,
            JulesApiClient julesApiClient) {
        this.queueManager = queueManager;
        this.repositoriesConfig = repositoriesConfig;
        this.crashlyticsService = crashlyticsService;
        this.gitHubClientService = gitHubClientService;
        this.triageService = triageService;
        this.issueTemplateBuilder = issueTemplateBuilder;
        this.julesApiClient = julesApiClient;
    }

    @Scheduled(fixedDelayString = "#{repositoriesConfigProperties.globalSettings.checkIntervalMinutes * 60000}", initialDelayString = "${agent.daemon.initial-delay:1000}")
    public void runAnalysisLoop() {
        try {
            processNextInQueue();
        } catch (Exception e) {
            log.error("Unexpected error in agent analysis loop daemon: {}", e.getMessage(), e);
        }
    }

    public boolean processNextInQueue() {
        if (!repositoriesConfig.getGlobalSettings().isAutoTriageEnabled()) {
            log.info("Auto triage is disabled in global settings. Skipping analysis loop execution.");
            return false;
        }

        RepositoryConfig repo = queueManager.pollNextRepository();
        if (repo == null) {
            log.info("No enabled repositories available in queue.");
            return false;
        }

        log.info("Starting analysis loop execution for repository: {}", repo.getName());
        try {
            executeTriagePipeline(repo);
            delegateApiCalls(repo);
            log.info("Successfully completed analysis loop execution for repository: {}", repo.getName());
            return true;
        } catch (Exception e) {
            log.error("Error occurred while processing repository '{}': {}", repo.getName(), e.getMessage(), e);
            return false;
        }
    }

    protected void executeTriagePipeline(RepositoryConfig repo) {
        if (crashlyticsService == null || gitHubClientService == null || triageService == null || issueTemplateBuilder == null) {
            log.warn("Analysis services are not wired; repository '{}' was not inspected.", repo.getName());
            return;
        }

        String owner = repo.getGithub().getOwner();
        String repository = repo.getGithub().getRepo();
        if (owner == null || owner.isBlank() || repository == null || repository.isBlank()) {
            log.warn("Repository '{}' has no GitHub owner/repo configuration; skipping.", repo.getName());
            return;
        }
        if (!gitHubClientService.isConfigured()) {
            log.warn("GITHUB_TOKEN is not configured; '{}' can be inspected but issues will not be created.", repo.getName());
        }

        int findings = 0;
        if (repo.getAudit().isEnabled()) {
            reviewOpenIssues(owner, repository);
        }
        if (repo.getCrashlytics().isEnabled()) {
            List<CrashEvent> crashes = crashlyticsService.getUnresolvedCrashes(
                    repo.getCrashlytics().getAppId(), null, repo.getCrashlytics().getMinErrorThreshold());
            for (CrashEvent crash : crashes) {
                findings += handleCrash(owner, repository, crash);
            }
        }

        if (repo.getGithubActions().isEnabled()) {
            findings += inspectFailedWorkflowRuns(repo, owner, repository);
        }
        if (repo.getAudit().isEnabled()) {
            findings += inspectRepositoryFiles(repo, owner, repository);
        }
        log.info("Repository '{}' inspection completed: {} actionable finding(s).", repo.getName(), findings);
    }

    protected void delegateApiCalls(RepositoryConfig repo) {
        log.info("Delegation is performed immediately after issue creation for repository '{}'.", repo.getName());
    }

    private int handleCrash(String owner, String repo, CrashEvent crash) {
        TriageResult result = triageService.triageCrashEvent(owner, repo, crash);
        log.info("Crash '{}' triage decision: {} ({})", crash.getCrashId(), result.getDecision(), result.getReasoning());
        if (result.getDecision() == TriageDecision.IGNORE_DUPLICATE) {
            return 0;
        }
        CreateIssueRequest request = issueTemplateBuilder.buildCrashIssueRequest(crash);
        return publishFinding(owner, repo, request, result, crash.getCrashId());
    }

    private int inspectFailedWorkflowRuns(RepositoryConfig config, String owner, String repo) {
        GitHubWorkflowRunsResponse response = gitHubClientService.getWorkflowRuns(owner, repo);
        if (response == null || response.getWorkflowRuns() == null) {
            return 0;
        }
        int findings = 0;
        for (GitHubWorkflowRun run : response.getWorkflowRuns()) {
            if (!"completed".equalsIgnoreCase(run.getStatus()) || !"failure".equalsIgnoreCase(run.getConclusion())) {
                continue;
            }
            if (!config.getGithubActions().getWorkflowsToMonitor().isEmpty()
                    && !isMonitoredWorkflow(config, run)) {
                continue;
            }
            String title = "CI failure: " + (run.getName() != null ? run.getName() : "workflow")
                    + " on " + (run.getHeadBranch() != null ? run.getHeadBranch() : "unknown branch");
            String logs = gitHubClientService.getWorkflowRunLogs(owner, repo, run.getId());
            TriageResult result = triageService.triageLogOutput(owner, repo, title, logs);
            log.info("Workflow '{}' run {} triage decision: {} ({})", run.getName(), run.getId(), result.getDecision(), result.getReasoning());
            if (result.getDecision() == TriageDecision.IGNORE_DUPLICATE) {
                continue;
            }
            CreateIssueRequest request = issueTemplateBuilder.buildGenericLogIssueRequest(
                    title, result.getReasoning(), logs, "GitHub Actions");
            findings += publishFinding(owner, repo, request, result, "workflow-" + run.getId());
        }
        return findings;
    }

    private void reviewOpenIssues(String owner, String repo) {
        List<GitHubIssue> issues = gitHubClientService.getIssues(owner, repo, "open");
        if (issues == null || issues.isEmpty()) {
            log.info("GitHub issue review for {}/{}: no open issues found.", owner, repo);
            return;
        }
        int reviewed = 0;
        for (GitHubIssue issue : issues) {
            if (issue.getPullRequest() != null) {
                continue;
            }
            String issueText = "GitHub issue #" + issue.getNumber() + ": " + issue.getTitle()
                    + "\n\n" + (issue.getBody() == null ? "" : issue.getBody());
            TriageResult result = triageService.triageLogOutput(owner, repo,
                    "Review recommendation for issue #" + issue.getNumber(), issueText);
            log.info("Reviewed GitHub issue #{} '{}': {} ({})", issue.getNumber(), issue.getTitle(),
                    result.getDecision(), result.getReasoning());
            reviewed++;
        }
        log.info("GitHub issue review for {}/{} completed: {} issue(s) reviewed.", owner, repo, reviewed);
    }

    private int inspectRepositoryFiles(RepositoryConfig config, String owner, String repo) {
        int findings = 0;
        for (String path : config.getAudit().getRepositoryFiles()) {
            try {
                GitHubContent content = gitHubClientService.getRepositoryFile(owner, repo, path);
                String fileText = gitHubClientService.decodeRepositoryFile(content);
                if (fileText.isBlank()) {
                    log.warn("Configured audit file '{}' is empty or unavailable.", path);
                    continue;
                }
                String title = "Configuration review: " + path;
                TriageResult result = triageService.triageLogOutput(owner, repo, title,
                        "Review this repository configuration for lint, test, security, and CI reliability recommendations.\n\n"
                                + fileText);
                log.info("Configuration file '{}' recommendation: {} ({})", path,
                        result.getDecision(), result.getReasoning());
                if (result.getDecision() != TriageDecision.IGNORE_DUPLICATE) {
                    CreateIssueRequest request = issueTemplateBuilder.buildGenericLogIssueRequest(
                            title, result.getReasoning(), fileText, "Repository configuration audit");
                    findings += publishFinding(owner, repo, request, result, "config-" + path);
                }
            } catch (Exception e) {
                log.warn("Unable to inspect configured audit file '{}' in {}/{}: {}", path, owner, repo, e.getMessage());
            }
        }
        return findings;
    }

    private boolean isMonitoredWorkflow(RepositoryConfig config, GitHubWorkflowRun run) {
        return config.getGithubActions().getWorkflowsToMonitor().stream()
                .anyMatch(workflow -> workflow.equalsIgnoreCase(run.getName())
                        || (run.getPath() != null && run.getPath().endsWith("/" + workflow))
                        || (run.getPath() != null && run.getPath().equalsIgnoreCase(workflow)));
    }

    private int publishFinding(String owner, String repo, CreateIssueRequest request, TriageResult result, String findingId) {
        if (!gitHubClientService.isConfigured()) {
            log.warn("Finding '{}' was not published because GITHUB_TOKEN is missing.", findingId);
            return 1;
        }
        GitHubIssue issue = gitHubClientService.createIssue(owner, repo, request);
        log.info("Created GitHub issue #{} for finding '{}'.", issue != null ? issue.getNumber() : "unknown", findingId);

        if (result.getDecision() == TriageDecision.RESOLVABLE_BY_AGENT
                && julesApiClient != null && julesApiClient.isConfigured() && issue != null) {
            String branch = "fix/" + findingId.toLowerCase().replaceAll("[^a-z0-9-]+", "-");
            julesApiClient.delegateIssue("https://github.com/" + owner + "/" + repo, issue.getNumber(), branch);
            log.info("Delegated issue #{} to Jules.", issue.getNumber());
        }
        return 1;
    }
}
