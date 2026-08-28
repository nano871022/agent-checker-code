package com.codeauditor.agent.triage;

import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.codeauditor.agent.crashlytics.dto.StackFrame;
import com.codeauditor.agent.github.dto.CreateIssueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssueTemplateBuilderTest {

    private IssueTemplateBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new IssueTemplateBuilder();
    }

    @Test
    void testBuildCrashIssueRequest_FullData() {
        StackFrame frame = StackFrame.builder()
                .packageName("com.codeauditor.agent")
                .className("com.codeauditor.agent.triage.TriageService")
                .methodName("triageCrashEvent")
                .fileName("TriageService.java")
                .lineNumber(42)
                .build();

        CrashEvent crashEvent = CrashEvent.builder()
                .crashId("CRASH-123")
                .appId("com.codeauditor.app")
                .appVersion("1.0.0")
                .exceptionType("java.lang.NullPointerException")
                .message("Object reference is null")
                .stackTrace("java.lang.NullPointerException: Object reference is null\n\tat com.codeauditor.agent.triage.TriageService.triageCrashEvent(TriageService.java:42)")
                .eventCount(15)
                .fingerprint("fp-abc-123")
                .primaryFrame(frame)
                .build();

        CreateIssueRequest request = builder.buildCrashIssueRequest(crashEvent);

        assertNotNull(request);
        assertEquals("java.lang.NullPointerException: Object reference is null", request.getTitle());
        assertTrue(request.getLabels().contains("bug"));
        assertTrue(request.getLabels().contains("automated-triage"));

        String body = request.getBody();
        assertNotNull(body);
        assertTrue(body.contains("## 📌 Problem Overview"));
        assertTrue(body.contains("- **Crash ID:** CRASH-123"));
        assertTrue(body.contains("- **App Version:** 1.0.0"));
        assertTrue(body.contains("- **Event Count:** 15"));
        assertTrue(body.contains("- **Fingerprint:** fp-abc-123"));

        assertTrue(body.contains("## 📜 Stack Trace"));
        assertTrue(body.contains("java.lang.NullPointerException: Object reference is null"));

        assertTrue(body.contains("## 📍 Affected Location"));
        assertTrue(body.contains("- **Class:** com.codeauditor.agent.triage.TriageService"));
        assertTrue(body.contains("- **Method:** triageCrashEvent"));
        assertTrue(body.contains("- **File:** TriageService.java"));
        assertTrue(body.contains("- **Line:** 42"));

        assertTrue(body.contains("## 📋 Task Checklist for Jules"));
        assertTrue(body.contains("- [ ] Investigate the root cause of java.lang.NullPointerException."));
        assertTrue(body.contains("- [ ] Locate affected code file and method."));
        assertTrue(body.contains("- [ ] Implement crash prevention / fix logic."));
        assertTrue(body.contains("- [ ] Add test cases to prevent regression."));
        assertTrue(body.contains("- [ ] Run build `./mvnw clean test` to ensure all tests pass."));
    }

    @Test
    void testBuildCrashIssueRequest_NullOptionalFields() {
        CrashEvent crashEvent = CrashEvent.builder()
                .eventCount(1)
                .build();

        CreateIssueRequest request = builder.buildCrashIssueRequest(crashEvent);

        assertNotNull(request);
        assertEquals("Crash", request.getTitle());

        String body = request.getBody();
        assertNotNull(body);
        assertTrue(body.contains("## 📌 Problem Overview"));
        assertTrue(body.contains("- **Crash ID:** N/A"));
        assertTrue(body.contains("- **App Version:** N/A"));
        assertTrue(body.contains("_No stack trace available._"));
        assertTrue(body.contains("_No primary frame information available._"));
        assertTrue(body.contains("## 📋 Task Checklist for Jules"));
    }

    @Test
    void testBuildCrashIssueRequest_NullCrashEvent_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildCrashIssueRequest(null));
    }

    @Test
    void testBuildGenericLogIssueRequest() {
        String title = "CI Action Failed: Build #88";
        String summary = "Maven compile failed on module agent-core";
        String logContent = "ERROR: Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin";
        String source = "GitHub Action Workflow: Java CI";

        CreateIssueRequest request = builder.buildGenericLogIssueRequest(title, summary, logContent, source);

        assertNotNull(request);
        assertEquals(title, request.getTitle());
        assertTrue(request.getLabels().contains("ci-failure"));

        String body = request.getBody();
        assertNotNull(body);
        assertTrue(body.contains("## 📌 Problem Overview"));
        assertTrue(body.contains(summary));
        assertTrue(body.contains("**Source:** " + source));
        assertTrue(body.contains("## 📜 Log Output"));
        assertTrue(body.contains(logContent));
        assertTrue(body.contains("## 📍 Affected Location"));
        assertTrue(body.contains("## 📋 Task Checklist for Jules"));
        assertTrue(body.contains("- [ ] Analyze log output and pinpoint root cause."));
    }

    @Test
    void testBuildGenericLogIssueRequest_DefaultTitleAndNullFields() {
        CreateIssueRequest request = builder.buildGenericLogIssueRequest(null, null, null, null);

        assertNotNull(request);
        assertEquals("CI/Log Failure Report", request.getTitle());
        String body = request.getBody();
        assertTrue(body.contains("_No log output provided._"));
        assertTrue(body.contains("## 📋 Task Checklist for Jules"));
    }
}
