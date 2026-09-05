package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.dev.japl.java.spring.agentcheckercode.crashlytics.dto.StackFrame;
import co.dev.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.dev.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageDecision;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageAnalysis;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private static final Set<String> UNRESOLVABLE_EXCEPTIONS = Set.of(
            "java.lang.OutOfMemoryError",
            "java.lang.StackOverflowError",
            "java.lang.VirtualMachineError",
            "java.lang.InternalError",
            "java.lang.UnknownError"
    );

    private final GitHubClientService gitHubClientService;
    private final CrashAnalysisAiService crashAnalysisAiService;
    private final String internalPackagePrefix;

    public TriageService(
            GitHubClientService gitHubClientService,
            CrashAnalysisAiService crashAnalysisAiService,
            @Value("${agent.triage.internal-package-prefix:com.codeauditor}") String internalPackagePrefix) {
        this.gitHubClientService = gitHubClientService;
        this.crashAnalysisAiService = crashAnalysisAiService;
        this.internalPackagePrefix = internalPackagePrefix;
    }

    /**
     * Triage a CrashEvent using deterministic rules first, and LLM fallback if inconclusive.
     */
    public TriageResult triageCrashEvent(String owner, String repo, CrashEvent crashEvent) {
        if (crashEvent == null) {
            return new TriageResult(TriageDecision.IGNORE_DUPLICATE, "Crash event is null");
        }

        String exceptionType = crashEvent.getExceptionType();
        String exceptionMessage = crashEvent.getMessage();
        String title = (exceptionType != null ? exceptionType : "Crash") + ": " + (exceptionMessage != null ? exceptionMessage : "");

        // 1. Check for Duplicate Issue in GitHub repository
        Long duplicateIssueId = checkDuplicateIssue(owner, repo, title, exceptionMessage);
        if (duplicateIssueId != null) {
            log.info("Crash event '{}' determined to be duplicate of issue #{}", crashEvent.getCrashId(), duplicateIssueId);
            return new TriageResult(
                    TriageDecision.IGNORE_DUPLICATE,
                    "Duplicate issue already exists on GitHub (Issue #" + duplicateIssueId + ")",
                    duplicateIssueId,
                    title
            );
        }

        // 2. Deterministic rule check for unresolvable system/fatal exceptions
        if (exceptionType != null && UNRESOLVABLE_EXCEPTIONS.contains(exceptionType)) {
            log.info("Crash event '{}' contains fatal exception type '{}' requiring human intervention.", crashEvent.getCrashId(), exceptionType);
            return new TriageResult(
                    TriageDecision.REQUIRES_HUMAN_INTERVENTION,
                    "Deterministic rule triggered: Exception '" + exceptionType + "' requires human infrastructure/system intervention.",
                    null,
                    title
            );
        }

        // 3. Package boundary analysis: check primary frame and stack trace string for internal packages
        StackFrame primaryFrame = crashEvent.getPrimaryFrame();
        String stackTraceStr = crashEvent.getStackTrace();
        boolean hasInternalPackage = false;

        if (primaryFrame != null) {
            hasInternalPackage = isInternalFrame(primaryFrame.getPackageName(), primaryFrame.getClassName());
        }

        if (!hasInternalPackage && stackTraceStr != null && !stackTraceStr.isBlank()) {
            hasInternalPackage = stackTraceStr.contains(internalPackagePrefix);
        }

        if (!hasInternalPackage) {
            log.info("Crash event '{}' stack trace contains no internal package frames under prefix '{}'.", crashEvent.getCrashId(), internalPackagePrefix);
            return new TriageResult(
                    TriageDecision.REQUIRES_HUMAN_INTERVENTION,
                    "Stack trace is exclusively in third-party or OS libraries; no internal code frames (" + internalPackagePrefix + ") affected.",
                    null,
                    title
            );
        }

        // 4. Fallback to LLM evaluation via CrashAnalysisAiService
        String fullAnalysisText = title + "\n" + (stackTraceStr != null ? stackTraceStr : "");
        return evaluateWithAiService(fullAnalysisText, title);
    }

    /**
     * Triage arbitrary log / CI output text.
     */
    public TriageResult triageLogOutput(String owner, String repo, String title, String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return new TriageResult(TriageDecision.IGNORE_DUPLICATE, "Log content is empty");
        }

        Long duplicateIssueId = checkDuplicateIssue(owner, repo, title, null);
        if (duplicateIssueId != null) {
            return new TriageResult(
                    TriageDecision.IGNORE_DUPLICATE,
                    "Duplicate issue already exists on GitHub (Issue #" + duplicateIssueId + ")",
                    duplicateIssueId,
                    title
            );
        }

        // Check for deterministic unresolvable errors in log
        for (String unresolvable : UNRESOLVABLE_EXCEPTIONS) {
            if (logContent.contains(unresolvable)) {
                return new TriageResult(
                        TriageDecision.REQUIRES_HUMAN_INTERVENTION,
                        "Log contains fatal exception: " + unresolvable,
                        null,
                        title
                );
            }
        }

        return evaluateWithAiService(logContent, title);
    }

    private TriageResult evaluateWithAiService(String textToAnalyze, String title) {
        try {
            log.info("Delegating triage evaluation to LLM via CrashAnalysisAiService.");
                TriageAnalysis analysis = crashAnalysisAiService.analyzeCrash(textToAnalyze);
                TriageDecision finalDecision = analysis != null && analysis.getDecision() != null
                    ? analysis.getDecision() : TriageDecision.RESOLVABLE_BY_AGENT;
                String detailedReasoning = analysis != null && analysis.getSummary() != null
                    ? analysis.getSummary() : "AI evaluation did not provide a summary.";

            return new TriageResult(
                    finalDecision,
                    detailedReasoning,
                    null,
                    title,
                    analysis
            );
        } catch (Exception e) {
            log.error("Error during AI triage evaluation, defaulting to RESOLVABLE_BY_AGENT: {}", e.getMessage(), e);
            return new TriageResult(
                    TriageDecision.RESOLVABLE_BY_AGENT,
                    "AI evaluation fallback due to error: " + e.getMessage(),
                    null,
                    title
            );
        }
    }

    private Long checkDuplicateIssue(String owner, String repo, String title, String message) {
        if (owner == null || repo == null || gitHubClientService == null) {
            return null;
        }

        try {
            List<GitHubIssue> openIssues = gitHubClientService.getIssues(owner, repo, "open");
            if (openIssues == null || openIssues.isEmpty()) {
                return null;
            }

            for (GitHubIssue issue : openIssues) {
                if (issue.getTitle() != null) {
                    if (title != null && !title.isBlank() && issue.getTitle().toLowerCase().contains(title.toLowerCase())) {
                        return issue.getNumber();
                    }
                    if (message != null && !message.isBlank() && issue.getTitle().toLowerCase().contains(message.toLowerCase())) {
                        return issue.getNumber();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check duplicate issues on GitHub for {}/{}: {}", owner, repo, e.getMessage());
        }

        return null;
    }

    private boolean isInternalFrame(String packageName, String className) {
        if (packageName != null && packageName.startsWith(internalPackagePrefix)) {
            return true;
        }
        return className != null && className.startsWith(internalPackagePrefix);
    }
}
