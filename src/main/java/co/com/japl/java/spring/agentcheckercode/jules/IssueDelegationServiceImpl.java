package co.com.japl.java.spring.agentcheckercode.jules;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import co.com.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.com.japl.java.spring.agentcheckercode.jules.dto.JulesTaskResponse;
import co.com.japl.java.spring.agentcheckercode.repository.RepositoryWorkspaceService;
import co.com.japl.java.spring.agentcheckercode.stitch.StitchApiClient;
import co.com.japl.java.spring.agentcheckercode.stitch.dto.StitchTransformResponse;
import co.com.japl.java.spring.agentcheckercode.utils.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueDelegationServiceImpl implements IssueDelegationService {

    private final RepositoriesConfigProperties repositoriesConfig;
    private final GitHubClientService gitHubClientService;
    private final JulesApiClient julesApiClient;
    private final StitchApiClient stitchApiClient;
    private final RepositoryWorkspaceService repositoryWorkspaceService;

    @Override
    public void delegateApiCalls(RepositoryConfig repo) {
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
        JulesTaskResponse response = julesApiClient.delegateIssue(
                String.format(Constants.URL_GITHUB_PROJECT, owner, repository),
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
                String.format(Constants.URL_GITHUB_PROJECT, owner, repository),
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

    private boolean hasLabel(GitHubIssue issue, String expectedLabel) {
        return issue != null && issue.getLabels() != null
                && issue.getLabels().stream()
                        .map(GitHubIssue.Label::getName)
                        .filter(Objects::nonNull)
                        .anyMatch(label -> label.equalsIgnoreCase(expectedLabel));
    }
}
