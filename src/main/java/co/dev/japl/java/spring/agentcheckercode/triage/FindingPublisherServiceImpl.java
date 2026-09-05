package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.dev.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.dev.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageDecision;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageResult;
import co.dev.japl.java.spring.agentcheckercode.utils.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FindingPublisherServiceImpl implements FindingPublisherService {

    private final GitHubClientService gitHubClientService;

    @Override
    public int publishFinding(String owner, String repo, CreateIssueRequest request, TriageResult result,
                               String findingId) {
        if (gitHubClientService == null || !gitHubClientService.isConfigured()) {
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

    @Override
    public void addTriageComment(String owner, String repo, Long issueNumber, TriageResult result) {
        if (gitHubClientService == null) {
            return;
        }
        String detailedAnalysis = (result != null && result.getDetailedAnalysis() != null)
                ? result.getDetailedAnalysis()
                : "No detailed analysis provided.";
        String decision = (result != null && result.getDecision() != null)
                ? String.valueOf(result.getDecision())
                : "N/A";

        Map<String, Object> vars = Map.of(
                "decision", decision,
                "detailedAnalysis", detailedAnalysis
        );
        String comment = PromptLoader.renderPrompt("prompts/triage-comment.prompt", vars).trim();

        try {
            gitHubClientService.addIssueComment(owner, repo, issueNumber, comment);
            gitHubClientService.addIssueLabel(owner, repo, issueNumber, "triage-completed");
            String decisionLabel = (result != null && result.getDecision() == TriageDecision.RESOLVABLE_BY_AGENT)
                    ? "triage-resolvable-by-agent"
                    : "triage-human-intervention";
            gitHubClientService.addIssueLabel(owner, repo, issueNumber, decisionLabel);
            log.info("Added automated triage comment to GitHub issue #{}.", issueNumber);
        } catch (Exception e) {
            log.warn("Unable to add automated triage comment to GitHub issue #{}: {}", issueNumber, e.getMessage());
        }
    }
}
