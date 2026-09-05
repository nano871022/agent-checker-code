package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.dev.japl.java.spring.agentcheckercode.crashlytics.dto.StackFrame;
import co.dev.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.dev.japl.java.spring.agentcheckercode.utils.PromptLoader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IssueTemplateBuilder {

    public CreateIssueRequest buildCrashIssueRequest(CrashEvent crashEvent) {
        if (crashEvent == null) {
            throw new IllegalArgumentException("CrashEvent must not be null");
        }

        String title = buildCrashTitle(crashEvent);
        String body = buildCrashBody(crashEvent);

        return CreateIssueRequest.builder()
                .title(title)
                .body(body)
                .labels(List.of("bug", "automated-triage"))
                .build();
    }

    public CreateIssueRequest buildGenericLogIssueRequest(String title, String summary, String logContent, String source) {
        if (title == null || title.isBlank()) {
            title = "CI/Log Failure Report";
        }

        StringBuilder sbOverview = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            sbOverview.append(summary.trim()).append("\n\n");
        }
        if (source != null && !source.isBlank()) {
            sbOverview.append("**Source:** ").append(source).append("\n\n");
        }
        String overviewSection = sbOverview.toString();

        String logOutputSection;
        if (logContent != null && !logContent.isBlank()) {
            logOutputSection = "```text\n" + logContent.trim() + "\n```\n";
        } else {
            logOutputSection = "_No log output provided._\n";
        }

        Map<String, Object> vars = Map.of(
                "overviewSection", overviewSection,
                "logOutputSection", logOutputSection
        );

        String body = PromptLoader.renderPrompt("prompts/generic-log-issue-body.prompt", vars);

        return CreateIssueRequest.builder()
                .title(title)
                .body(body)
                .labels(List.of("bug", "ci-failure", "automated-triage"))
                .build();
    }

    public String buildCrashTitle(CrashEvent crashEvent) {
        String exceptionType = crashEvent.getExceptionType() != null ? crashEvent.getExceptionType() : "Crash";
        String message = crashEvent.getMessage() != null ? crashEvent.getMessage() : "";
        if (message.isBlank()) {
            return exceptionType;
        }
        return exceptionType + ": " + message;
    }

    public String buildCrashBody(CrashEvent crashEvent) {
        String fingerprintSection = (crashEvent.getFingerprint() != null && !crashEvent.getFingerprint().isBlank())
                ? "- **Fingerprint:** " + crashEvent.getFingerprint() + "\n"
                : "";

        String stackTraceSection;
        if (crashEvent.getStackTrace() != null && !crashEvent.getStackTrace().isBlank()) {
            stackTraceSection = "```java\n" + crashEvent.getStackTrace().trim() + "\n```\n";
        } else {
            stackTraceSection = "_No stack trace available._\n";
        }

        String affectedLocationSection;
        StackFrame primaryFrame = crashEvent.getPrimaryFrame();
        if (primaryFrame != null) {
            StringBuilder sf = new StringBuilder();
            sf.append("- **Class:** ").append(primaryFrame.getClassName() != null ? primaryFrame.getClassName() : "N/A").append("\n");
            sf.append("- **Method:** ").append(primaryFrame.getMethodName() != null ? primaryFrame.getMethodName() : "N/A").append("\n");
            sf.append("- **File:** ").append(primaryFrame.getFileName() != null ? primaryFrame.getFileName() : "N/A").append("\n");
            sf.append("- **Line:** ").append(primaryFrame.getLineNumber() > 0 ? primaryFrame.getLineNumber() : "N/A").append("\n");
            if (primaryFrame.getPackageName() != null && !primaryFrame.getPackageName().isBlank()) {
                sf.append("- **Package:** ").append(primaryFrame.getPackageName()).append("\n");
            }
            affectedLocationSection = sf.toString().trim();
        } else {
            affectedLocationSection = "_No primary frame information available._";
        }

        String exceptionTypeContext = crashEvent.getExceptionType() != null ? crashEvent.getExceptionType() : "the crash";

        Map<String, Object> vars = Map.of(
                "crashId", crashEvent.getCrashId() != null ? crashEvent.getCrashId() : "N/A",
                "appVersion", crashEvent.getAppVersion() != null ? crashEvent.getAppVersion() : "N/A",
                "exceptionType", crashEvent.getExceptionType() != null ? crashEvent.getExceptionType() : "N/A",
                "message", crashEvent.getMessage() != null ? crashEvent.getMessage() : "N/A",
                "eventCount", crashEvent.getEventCount(),
                "fingerprintSection", fingerprintSection,
                "stackTraceSection", stackTraceSection,
                "affectedLocationSection", affectedLocationSection,
                "exceptionTypeContext", exceptionTypeContext
        );

        return PromptLoader.renderPrompt("prompts/crash-issue-body.prompt", vars);
    }
}
