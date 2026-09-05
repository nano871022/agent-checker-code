package com.codeauditor.agent.daemon;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.codeauditor.agent.config.RepositoriesConfigProperties;
import com.codeauditor.agent.config.RepositoriesConfigProperties.RepositoryConfig;
import com.codeauditor.agent.crashlytics.FirebaseCrashlyticsService;
import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.codeauditor.agent.github.GitHubClientService;
import com.codeauditor.agent.github.dto.CreateIssueRequest;
import com.codeauditor.agent.github.dto.GitHubContent;
import com.codeauditor.agent.github.dto.GitHubIssue;
import com.codeauditor.agent.github.dto.GitHubWorkflowRun;
import com.codeauditor.agent.github.dto.GitHubWorkflowRunsResponse;
import com.codeauditor.agent.jules.JulesApiClient;
import com.codeauditor.agent.jules.dto.JulesTaskResponse;
import com.codeauditor.agent.queue.RepositoryQueueManager;
import com.codeauditor.agent.repository.RepositoryWorkspaceService;
import com.codeauditor.agent.stitch.StitchApiClient;
import com.codeauditor.agent.stitch.dto.StitchTransformResponse;
import com.codeauditor.agent.triage.IssueTemplateBuilder;
import com.codeauditor.agent.triage.TriageService;
import com.codeauditor.agent.triage.dto.TriageDecision;
import com.codeauditor.agent.triage.dto.TriageResult;
import com.codeauditor.agent.utils.Costants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentLoopDaemon {

    private final RepositoryQueueManager queueManager;
    private final RepositoriesConfigProperties repositoriesConfig;
    private final FirebaseCrashlyticsService crashlyticsService;
    private final GitHubClientService gitHubClientService;
    private final TriageService triageService;
    private final IssueTemplateBuilder issueTemplateBuilder;
    private final JulesApiClient julesApiClient;
    private final RepositoryWorkspaceService repositoryWorkspaceService;
    private final StitchApiClient stitchApiClient;

    public AgentLoopDaemon(RepositoryQueueManager queueManager, RepositoriesConfigProperties repositoriesConfig) {
        this(queueManager, repositoriesConfig, null, null, null, null, null, null, null);
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
        if (crashlyticsService == null || gitHubClientService == null || triageService == null
                || issueTemplateBuilder == null) {
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
            log.warn("GITHUB_TOKEN is not configured; '{}' can be inspected but issues will not be created.",
                    repo.getName());
        }

        Path localRepository = repositoryWorkspaceService == null ? null
                : repositoryWorkspaceService.prepareRepository(repo,
                        repositoriesConfig.getGlobalSettings().getRepositoriesBasePath());

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
            findings += inspectRepositoryFiles(repo, owner, repository, localRepository);
        }
        log.info("Repository '{}' inspection completed: {} actionable finding(s).", repo.getName(), findings);
    }

    protected void delegateApiCalls(RepositoryConfig repo) {
        if (hasEmptyService()) {
            log.warn("Issue delegation skipped for '{}': GitHub or Jules is not configured.", repo.getName());
            return;
        }

        String owner = repo.getGithub().getOwner();
        String repository = repo.getGithub().getRepo();
        List<GitHubIssue> issues = gitHubClientService.getIssues(owner, repository, "open");
        if (isInvalidIssues(issues)) {
            log.info("No open issues available for delegation in {}/{}.", owner, repository);
            return;
        }

        Path localRepository = repositoryWorkspaceService == null ? null
                : repositoryWorkspaceService.prepareRepository(repo,
                        repositoriesConfig.getGlobalSettings().getRepositoriesBasePath());
        for (GitHubIssue issue : issues) {
            if (!isAutomatedIssue(issue)) {
                continue;
            }
            try {
                if (isDesignIssue(issue)) {
                    delegateDesignIssue(repo, localRepository, issue);
                } else {
                    delegateDevelopmentIssue(owner, repository, issue);
                }
            } catch (Exception e) {
                log.warn("Unable to delegate issue #{} '{}': {}", issue.getNumber(), issue.getTitle(), e.getMessage());
            }
        }
    }

    private boolean isInvalidIssues(List<GitHubIssue> issues) {
        return issues == null || issues.isEmpty();
    }

    private boolean hasEmptyService() {
        return gitHubClientService == null || !gitHubClientService.isConfigured() ||
                julesApiClient == null || !julesApiClient.isConfigured() ||
                stitchApiClient == null || !stitchApiClient.isConfigured();
    }

    private void delegateDevelopmentIssue(String owner, String repository, GitHubIssue issue) {
        String branch = buildDelegationBranch("fix", issue);
        JulesTaskResponse response = julesApiClient.delegateIssue(
                String.format(Costants.URL_GITHUB_PROJECT, owner, repository),
            issue.getNumber(), "main", buildJulesPrompt(issue, null));
        log.info("Development issue #{} delegated to Jules with task '{}'.", issue.getNumber(),
                response == null ? "unknown" : response.getTaskId());
        addDelegationComment(owner, repository, issue, "Jules", response);
    }

    private void delegateDesignIssue(RepositoryConfig repo, Path localRepository, GitHubIssue issue) {
        if (stitchApiClient == null || !stitchApiClient.isConfigured()) {
            log.warn("Design issue #{} skipped: Stitch is not configured.", issue.getNumber());
            return;
        }
        String themeTokens = readWorkspaceFile(localRepository, repo.getStitch().getThemeConfigPath());
        String uiCode = readWorkspaceFile(localRepository, repo.getStitch().getUiComponentsDir());
        if (uiCode.isBlank()) {
            uiCode = issue.getBody() == null ? "" : issue.getBody();
        }
        StitchTransformResponse stitchResponse = stitchApiClient.transformUi(
                uiCode, themeTokens, "Implement the design requirements from this GitHub issue:\n" + issue.getTitle());
        if (!stitchApiClient.validateResponse(stitchResponse)) {
            log.warn("Design issue #{} was not delegated: Stitch returned an invalid response.", issue.getNumber());
            return;
        }

        String owner = repo.getGithub().getOwner();
        String repository = repo.getGithub().getRepo();
        JulesTaskResponse response = julesApiClient.delegateIssue(
                String.format(Costants.URL_GITHUB_PROJECT, owner, repository),
            issue.getNumber(), repo.getGithub().getDefaultBranch(),
                buildJulesPrompt(issue, stitchResponse.getTransformedCode()));
        log.info("Design issue #{} transformed by Stitch and delegated to Jules with task '{}'.",
                issue.getNumber(), response == null ? "unknown" : response.getTaskId());
        addDelegationComment(owner, repository, issue, "Stitch -> Jules", response);
    }

    private boolean isAutomatedIssue(GitHubIssue issue) {
        if (issue == null || issue.getNumber() == null || issue.getLabels() == null) {
            return false;
        }
        return issue.getLabels().stream()
                .map(GitHubIssue.Label::getName)
                .filter(Objects::nonNull)
                .map(label -> label.toLowerCase(Locale.ROOT))
                .noneMatch("delegated-to-jules"::equals)
                && hasLabel(issue, "triage-resolvable-by-agent");
    }

    private boolean isDesignIssue(GitHubIssue issue) {
        String text = ((issue.getTitle() == null ? "" : issue.getTitle()) + " "
                + (issue.getBody() == null ? "" : issue.getBody())).toLowerCase(Locale.ROOT);
        Set<String> words = new java.util.HashSet<>(List.of(text.split("[^a-z0-9]+")));
        return Set.of("design", "ui", "ux", "theme", "layout", "color", "visual", "stitch")
                .stream().anyMatch(words::contains) || text.contains("material 3");
    }

    private String readWorkspaceFile(Path repository, String path) {
        if (repositoryWorkspaceService == null || path == null || path.isBlank()) {
            return "";
        }
        try {
            return repositoryWorkspaceService.readFile(repository, path);
        } catch (Exception e) {
            log.warn("Unable to read workspace file '{}': {}", path, e.getMessage());
            return "";
        }
    }

    private String buildDelegationBranch(String prefix, GitHubIssue issue) {
        return prefix + "/issue-" + issue.getNumber();
    }

    private String buildJulesPrompt(GitHubIssue issue, String transformedCode) {
        String prompt = "Implement GitHub issue #" + issue.getNumber() + ": " + issue.getTitle()
                + "\n\nIssue body:\n" + (issue.getBody() == null ? "" : issue.getBody());
        if (transformedCode != null && !transformedCode.isBlank()) {
            prompt += "\n\nStitch transformed code to apply in the repository:\n```\n"
                    + transformedCode + "\n```";
        }
        return prompt;
    }

    private void addDelegationComment(String owner, String repository, GitHubIssue issue,
            String agent, JulesTaskResponse response) {
        String taskId = response == null ? "unknown" : response.getTaskId();
        try {
            gitHubClientService.addIssueComment(owner, repository, issue.getNumber(),
                    "Delegated to " + agent + ". Jules task: " + taskId + ".");
            gitHubClientService.addIssueLabel(owner, repository, issue.getNumber(), "delegated-to-jules");
        } catch (Exception e) {
            log.warn("Unable to record delegation comment for issue #{}: {}", issue.getNumber(), e.getMessage());
        }
    }

    private int handleCrash(String owner, String repo, CrashEvent crash) {
        TriageResult result = triageService.triageCrashEvent(owner, repo, crash);
        log.info("Crash '{}' triage decision: {} ({})", crash.getCrashId(), result.getDecision(),
                result.getReasoning());
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
        for (GitHubWorkflowRun run : latestWorkflowRuns(config, response.getWorkflowRuns())) {
            if (!config.getGithubActions().getWorkflowsToMonitor().isEmpty()
                    && !isMonitoredWorkflow(config, run)) {
                continue;
            }
            if (!"completed".equalsIgnoreCase(run.getStatus())) {
                log.info("Latest workflow '{}' run {} is still '{}'; skipping triage.",
                        run.getName(), run.getId(), run.getStatus());
                continue;
            }
            if (!"failure".equalsIgnoreCase(run.getConclusion())) {
                log.info("Latest workflow '{}' run {} completed with '{}'; no finding created.",
                        run.getName(), run.getId(), run.getConclusion());
                continue;
            }
            String title = "CI failure: " + (run.getName() != null ? run.getName() : "workflow")
                    + " on " + (run.getHeadBranch() != null ? run.getHeadBranch() : "unknown branch");
            String logs = gitHubClientService.getWorkflowRunLogs(owner, repo, run.getId());
            String executionInfo = buildWorkflowExecutionInfo(run);
            TriageResult result = triageService.triageLogOutput(owner, repo, title,
                    executionInfo + "\n\n" + logs);
            log.info("Workflow '{}' run {} triage decision: {} ({})", run.getName(), run.getId(), result.getDecision(),
                    result.getReasoning());
            if (result.getDecision() == TriageDecision.IGNORE_DUPLICATE) {
                continue;
            }
            CreateIssueRequest request = issueTemplateBuilder.buildGenericLogIssueRequest(
                    title, null, executionInfo + "\n\n" + logs, "GitHub Actions");
            findings += publishFinding(owner, repo, request, result, "workflow-" + run.getId());
        }
        return findings;
    }

    private List<GitHubWorkflowRun> latestWorkflowRuns(RepositoryConfig config, List<GitHubWorkflowRun> runs) {
        Map<String, GitHubWorkflowRun> latestByWorkflow = new LinkedHashMap<>();
        runs.stream()
                .filter(run -> run != null && (config.getGithubActions().getWorkflowsToMonitor().isEmpty()
                        || isMonitoredWorkflow(config, run)))
                .sorted(Comparator.comparing(this::workflowRunTimestamp,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .forEach(run -> latestByWorkflow.putIfAbsent(workflowKey(run), run));
        return List.copyOf(latestByWorkflow.values());
    }

    private String workflowKey(GitHubWorkflowRun run) {
        if (run.getPath() != null && !run.getPath().isBlank()) {
            return run.getPath();
        }
        return run.getName() == null ? "workflow-" + run.getId() : run.getName();
    }

    private java.time.OffsetDateTime workflowRunTimestamp(GitHubWorkflowRun run) {
        return run.getUpdatedAt() != null ? run.getUpdatedAt() : run.getCreatedAt();
    }

    private String buildWorkflowExecutionInfo(GitHubWorkflowRun run) {
        return "Workflow execution information:\n"
                + "- Run ID: " + run.getId() + "\n"
                + "- Workflow: " + run.getName() + "\n"
                + "- Status: " + run.getStatus() + "\n"
                + "- Conclusion: " + run.getConclusion() + "\n"
                + "- Branch: " + run.getHeadBranch() + "\n"
                + "- Commit: " + run.getHeadSha() + "\n"
                + "- Updated at: " + run.getUpdatedAt() + "\n"
                + "- URL: " + run.getHtmlUrl();
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
            if (hasLabel(issue, "triage-completed")) {
                log.debug("Skipping already triaged GitHub issue #{} '{}'.", issue.getNumber(), issue.getTitle());
                continue;
            }
            String issueText = "GitHub issue #" + issue.getNumber() + ": " + issue.getTitle()
                    + "\n\n" + (issue.getBody() == null ? "" : issue.getBody());
            TriageResult result = triageService.triageLogOutput(owner, repo,
                    "Review recommendation for issue #" + issue.getNumber(), issueText);
            log.info("Reviewed GitHub issue #{} '{}': {} ({})", issue.getNumber(), issue.getTitle(),
                    result.getDecision(), result.getReasoning());
            if (gitHubClientService.isConfigured()) {
                addTriageComment(owner, repo, issue.getNumber(), result);
            }
            reviewed++;
        }
        log.info("GitHub issue review for {}/{} completed: {} issue(s) reviewed.", owner, repo, reviewed);
    }

    private int inspectRepositoryFiles(RepositoryConfig config, String owner, String repo, Path localRepository) {
        int findings = 0;
        for (String path : config.getAudit().getRepositoryFiles()) {
            try {
                String fileText = repositoryWorkspaceService == null ? ""
                        : repositoryWorkspaceService.readFile(localRepository, path);
                if (fileText.isBlank()) {
                    GitHubContent content = gitHubClientService.getRepositoryFile(owner, repo, path);
                    fileText = gitHubClientService.decodeRepositoryFile(content);
                }
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
                            title, null, fileText, "Repository configuration audit");
                    findings += publishFinding(owner, repo, request, result, "config-" + path);
                }
            } catch (Exception e) {
                log.warn("Unable to inspect configured audit file '{}' in {}/{}: {}", path, owner, repo,
                        e.getMessage());
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

    private boolean hasLabel(GitHubIssue issue, String expectedLabel) {
        return issue != null && issue.getLabels() != null
                && issue.getLabels().stream()
                        .map(GitHubIssue.Label::getName)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(label -> label.equalsIgnoreCase(expectedLabel));
    }

    private int publishFinding(String owner, String repo, CreateIssueRequest request, TriageResult result,
            String findingId) {
        if (!gitHubClientService.isConfigured()) {
            log.warn("Finding '{}' was not published because GITHUB_TOKEN is missing.", findingId);
            return 1;
        }
        GitHubIssue issue = gitHubClientService.createIssue(owner, repo, request);
        log.info("Created GitHub issue #{} for finding '{}'.", issue != null ? issue.getNumber() : "unknown",
                findingId);

        if (issue != null && issue.getNumber() != null) {
            addTriageComment(owner, repo, issue.getNumber(), result);
        }

        return 1;
    }

    private void addTriageComment(String owner, String repo, Long issueNumber, TriageResult result) {
        String comment = """
                ## Automated triage recommendation

                **Decision:** %s

                **Detailed analysis:**
                %s
                """.formatted(result.getDecision(),
                result.getDetailedAnalysis() == null ? "No detailed analysis provided." : result.getDetailedAnalysis())
                .trim();
        try {
            gitHubClientService.addIssueComment(owner, repo, issueNumber, comment);
            gitHubClientService.addIssueLabel(owner, repo, issueNumber, "triage-completed");
            String decisionLabel = result.getDecision() == TriageDecision.RESOLVABLE_BY_AGENT
                    ? "triage-resolvable-by-agent"
                    : "triage-human-intervention";
            gitHubClientService.addIssueLabel(owner, repo, issueNumber, decisionLabel);
            log.info("Added automated triage comment to GitHub issue #{}.", issueNumber);
        } catch (Exception e) {
            log.warn("Unable to add automated triage comment to GitHub issue #{}: {}", issueNumber, e.getMessage());
        }
    }
}
