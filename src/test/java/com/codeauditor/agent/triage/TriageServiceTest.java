package com.codeauditor.agent.triage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.codeauditor.agent.crashlytics.dto.StackFrame;
import com.codeauditor.agent.github.GitHubClientService;
import com.codeauditor.agent.github.dto.GitHubIssue;
import com.codeauditor.agent.triage.dto.TriageAnalysis;
import com.codeauditor.agent.triage.dto.TriageDecision;
import com.codeauditor.agent.triage.dto.TriageResult;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock
    private GitHubClientService gitHubClientService;

    @Mock
    private CrashAnalysisAiService crashAnalysisAiService;

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        triageService = new TriageService(gitHubClientService, crashAnalysisAiService, "com.codeauditor");
    }

    @Test
    void triageCrashEvent_duplicateIssue_returnsIgnoreDuplicate() {
        GitHubIssue existingIssue = new GitHubIssue();
        existingIssue.setNumber(42L);
        existingIssue.setTitle("NullPointerException: null pointer in UserProcessor");

        when(gitHubClientService.getIssues("owner", "repo", "open"))
                .thenReturn(List.of(existingIssue));

        CrashEvent crash = CrashEvent.builder()
                .crashId("crash-001")
                .exceptionType("NullPointerException")
                .message("null pointer in UserProcessor")
                .build();

        TriageResult result = triageService.triageCrashEvent("owner", "repo", crash);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.IGNORE_DUPLICATE);
        assertThat(result.getDuplicateOfIssueId()).isEqualTo(42L);
        verifyNoInteractions(crashAnalysisAiService);
    }

    @Test
    void triageCrashEvent_outOfMemoryError_returnsRequiresHumanIntervention() {
        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of());

        CrashEvent crash = CrashEvent.builder()
                .crashId("crash-002")
                .exceptionType("java.lang.OutOfMemoryError")
                .message("Java heap space")
                .build();

        TriageResult result = triageService.triageCrashEvent("owner", "repo", crash);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.REQUIRES_HUMAN_INTERVENTION);
        assertThat(result.getReasoning()).contains("Deterministic rule triggered");
        verifyNoInteractions(crashAnalysisAiService);
    }

    @Test
    void triageCrashEvent_externalPackageOnly_returnsRequiresHumanIntervention() {
        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of());

        StackFrame externalFrame = StackFrame.builder()
                .packageName("org.apache.commons")
                .className("org.apache.commons.StringUtils")
                .build();

        CrashEvent crash = CrashEvent.builder()
                .crashId("crash-003")
                .exceptionType("IllegalArgumentException")
                .message("Invalid arg")
                .primaryFrame(externalFrame)
                .stackTrace("at org.apache.commons.StringUtils.isEmpty(StringUtils.java:10)")
                .build();

        TriageResult result = triageService.triageCrashEvent("owner", "repo", crash);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.REQUIRES_HUMAN_INTERVENTION);
        assertThat(result.getReasoning()).contains("exclusively in third-party or OS libraries");
        verifyNoInteractions(crashAnalysisAiService);
    }

    @Test
    void triageCrashEvent_internalPackage_delegatesToAiService() {
        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of());
        when(crashAnalysisAiService.analyzeCrash(anyString())).thenReturn(TriageAnalysis.builder()
            .decision(TriageDecision.RESOLVABLE_BY_AGENT)
            .summary("Null object is used before initialization")
            .recommendedActions(List.of("Add a null guard", "Run the affected unit tests"))
            .build());

        StackFrame internalFrame = StackFrame.builder()
                .packageName("com.codeauditor.agent.service")
                .className("com.codeauditor.agent.service.UserService")
                .build();

        CrashEvent crash = CrashEvent.builder()
                .crashId("crash-004")
                .exceptionType("NullPointerException")
                .message("User object was null")
                .primaryFrame(internalFrame)
                .stackTrace("at com.codeauditor.agent.service.UserService.processUser(UserService.java:45)")
                .build();

        TriageResult result = triageService.triageCrashEvent("owner", "repo", crash);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.RESOLVABLE_BY_AGENT);
        verify(crashAnalysisAiService).analyzeCrash(contains("com.codeauditor.agent.service.UserService"));
    }

    @Test
    void triageLogOutput_fatalException_returnsRequiresHumanIntervention() {
        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of());

        String logContent = "Fatal error encountered: java.lang.OutOfMemoryError: Java heap space";

        TriageResult result = triageService.triageLogOutput("owner", "repo", "Build Failed", logContent);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.REQUIRES_HUMAN_INTERVENTION);
        verifyNoInteractions(crashAnalysisAiService);
    }

    @Test
    void triageLogOutput_normalLog_delegatesToAiService() {
        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of());
        when(crashAnalysisAiService.analyzeCrash(anyString())).thenReturn(TriageAnalysis.builder()
            .decision(TriageDecision.RESOLVABLE_BY_AGENT)
            .summary("Compilation syntax error")
            .testsToAdd(List.of("Run the compile task"))
            .build());

        String logContent = "Build failed: Syntax error on line 42 in Controller.java";

        TriageResult result = triageService.triageLogOutput("owner", "repo", "Compilation Error", logContent);

        assertThat(result.getDecision()).isEqualTo(TriageDecision.RESOLVABLE_BY_AGENT);
        verify(crashAnalysisAiService).analyzeCrash(logContent);
    }
}
