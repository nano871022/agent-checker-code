package co.com.japl.java.spring.agentcheckercode.triage;

import co.com.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.com.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.com.japl.java.spring.agentcheckercode.triage.dto.TriageDecision;
import co.com.japl.java.spring.agentcheckercode.triage.dto.TriageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindingPublisherServiceImplTest {

    @Mock
    private GitHubClientService gitHubClientService;

    private FindingPublisherServiceImpl publisherService;

    @BeforeEach
    void setUp() {
        publisherService = new FindingPublisherServiceImpl(gitHubClientService);
    }

    @Test
    void publishFinding_WhenGitHubConfigured_CreatesIssueAndAddsComment() {
        when(gitHubClientService.isConfigured()).thenReturn(true);
        GitHubIssue mockIssue = GitHubIssue.builder().number(123L).build();
        when(gitHubClientService.createIssue(eq("owner"), eq("repo"), any())).thenReturn(mockIssue);

        TriageResult result = new TriageResult(TriageDecision.RESOLVABLE_BY_AGENT, "Test analysis");

        CreateIssueRequest request = CreateIssueRequest.builder().title("Title").build();

        int res = publisherService.publishFinding("owner", "repo", request, result, "finding-1");

        assertEquals(1, res);
        verify(gitHubClientService).createIssue("owner", "repo", request);
        verify(gitHubClientService).addIssueComment(eq("owner"), eq("repo"), eq(123L), anyString());
        verify(gitHubClientService).addIssueLabel("owner", "repo", 123L, "triage-completed");
        verify(gitHubClientService).addIssueLabel("owner", "repo", 123L, "triage-resolvable-by-agent");
    }

    @Test
    void publishFinding_WhenGitHubNotConfigured_LogsAndReturnsOne() {
        when(gitHubClientService.isConfigured()).thenReturn(false);

        TriageResult result = new TriageResult(TriageDecision.RESOLVABLE_BY_AGENT, "Reason");
        CreateIssueRequest request = CreateIssueRequest.builder().title("Title").build();

        int res = publisherService.publishFinding("owner", "repo", request, result, "finding-1");

        assertEquals(1, res);
        verify(gitHubClientService, never()).createIssue(any(), any(), any());
    }
}
