package co.com.japl.java.spring.agentcheckercode.triage;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import co.com.japl.java.spring.agentcheckercode.crashlytics.FirebaseCrashlyticsService;
import co.com.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.com.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.com.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubContent;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubWorkflowRun;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubWorkflowRunsResponse;
import co.com.japl.java.spring.agentcheckercode.repository.RepositoryWorkspaceService;
import co.com.japl.java.spring.agentcheckercode.triage.dto.TriageDecision;
import co.com.japl.java.spring.agentcheckercode.triage.dto.TriageResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriagePipelineServiceImpl implements TriagePipelineService {

    private final RepositoriesConfigProperties repositoriesConfig;
    private final FirebaseCrashlyticsService crashlyticsService;
    private final GitHubClientService gitHubClientService;
    private final TriageService triageService;
    private final IssueTemplateBuilder issueTemplateBuilder;
    private final RepositoryWorkspaceService repositoryWorkspaceService;
    private final FindingPublisherService findingPublisherService;

    @Override
    public void executeTriagePipeline(RepositoryConfig repo) {
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

    private int handleCrash(String owner, String repo, CrashEvent crash) {
        TriageResult result = triageService.triageCrashEvent(owner, repo, crash);
        log.info("Crash '{}' triage decision: {} ({})", crash.getCrashId(), result.getDecision(),
                result.getReasoning());
        if (result.getDecision() == TriageDecision.IGNORE_DUPLICATE) {
            return 0;
        }
        CreateIssueRequest request = issueTemplateBuilder.buildCrashIssueRequest(crash);
        return findingPublisherService.publishFinding(owner, repo, request, result, crash.getCrashId());
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
            findings += findingPublisherService.publishFinding(owner, repo, request, result, "workflow-" + run.getId());
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
                findingPublisherService.addTriageComment(owner, repo, issue.getNumber(), result);
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
                    findings += findingPublisherService.publishFinding(owner, repo, request, result, "config-" + path);
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
}
