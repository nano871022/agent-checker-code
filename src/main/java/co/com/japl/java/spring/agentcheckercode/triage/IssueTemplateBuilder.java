package co.com.japl.java.spring.agentcheckercode.triage;

import co.com.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.com.japl.java.spring.agentcheckercode.crashlytics.dto.StackFrame;
import co.com.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import org.springframework.stereotype.Component;

import java.util.List;

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

        StringBuilder sb = new StringBuilder();

        sb.append("## 📌 Problem Overview\n\n");
        if (summary != null && !summary.isBlank()) {
            sb.append(summary.trim()).append("\n\n");
        }
        if (source != null && !source.isBlank()) {
            sb.append("**Source:** ").append(source).append("\n\n");
        }

        sb.append("## 📜 Log Output\n\n");
        if (logContent != null && !logContent.isBlank()) {
            sb.append("```text\n")
              .append(logContent.trim())
              .append("\n```\n\n");
        } else {
            sb.append("_No log output provided._\n\n");
        }

        sb.append("## 📍 Affected Location\n\n");
        sb.append("Refer to log output above for relevant files and stack traces.\n\n");

        sb.append("## 📋 Task Checklist for Jules\n\n");
        sb.append("- [ ] Analyze log output and pinpoint root cause.\n");
        sb.append("- [ ] Implement code fix in source repository.\n");
        sb.append("- [ ] Add or update unit tests to verify fix.\n");
        sb.append("- [ ] Verify build and tests pass locally before submitting PR.\n");

        return CreateIssueRequest.builder()
                .title(title)
                .body(sb.toString())
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
        StringBuilder sb = new StringBuilder();

        // 1. Problem Overview
        sb.append("## 📌 Problem Overview\n\n");
        sb.append("- **Crash ID:** ").append(crashEvent.getCrashId() != null ? crashEvent.getCrashId() : "N/A").append("\n");
        sb.append("- **App Version:** ").append(crashEvent.getAppVersion() != null ? crashEvent.getAppVersion() : "N/A").append("\n");
        sb.append("- **Exception Type:** ").append(crashEvent.getExceptionType() != null ? crashEvent.getExceptionType() : "N/A").append("\n");
        sb.append("- **Message:** ").append(crashEvent.getMessage() != null ? crashEvent.getMessage() : "N/A").append("\n");
        sb.append("- **Event Count:** ").append(crashEvent.getEventCount()).append("\n");
        if (crashEvent.getFingerprint() != null && !crashEvent.getFingerprint().isBlank()) {
            sb.append("- **Fingerprint:** ").append(crashEvent.getFingerprint()).append("\n");
        }
        sb.append("\n");

        // 2. Stack Trace
        sb.append("## 📜 Stack Trace\n\n");
        if (crashEvent.getStackTrace() != null && !crashEvent.getStackTrace().isBlank()) {
            sb.append("```java\n")
              .append(crashEvent.getStackTrace().trim())
              .append("\n```\n\n");
        } else {
            sb.append("_No stack trace available._\n\n");
        }

        // 3. Affected Lines / Location
        sb.append("## 📍 Affected Location\n\n");
        StackFrame primaryFrame = crashEvent.getPrimaryFrame();
        if (primaryFrame != null) {
            sb.append("- **Class:** ").append(primaryFrame.getClassName() != null ? primaryFrame.getClassName() : "N/A").append("\n");
            sb.append("- **Method:** ").append(primaryFrame.getMethodName() != null ? primaryFrame.getMethodName() : "N/A").append("\n");
            sb.append("- **File:** ").append(primaryFrame.getFileName() != null ? primaryFrame.getFileName() : "N/A").append("\n");
            sb.append("- **Line:** ").append(primaryFrame.getLineNumber() > 0 ? primaryFrame.getLineNumber() : "N/A").append("\n");
            if (primaryFrame.getPackageName() != null && !primaryFrame.getPackageName().isBlank()) {
                sb.append("- **Package:** ").append(primaryFrame.getPackageName()).append("\n");
            }
        } else {
            sb.append("_No primary frame information available._\n");
        }
        sb.append("\n");

        // 4. Task Checklist for Jules
        sb.append("## 📋 Task Checklist for Jules\n\n");
        sb.append("- [ ] Investigate the root cause of ").append(crashEvent.getExceptionType() != null ? crashEvent.getExceptionType() : "the crash").append(".\n");
        sb.append("- [ ] Locate affected code file and method.\n");
        sb.append("- [ ] Implement crash prevention / fix logic.\n");
        sb.append("- [ ] Add test cases to prevent regression.\n");
        sb.append("- [ ] Run build `./mvnw clean test` to ensure all tests pass.\n");

        return sb.toString();
    }
}
