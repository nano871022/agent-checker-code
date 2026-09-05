package co.com.japl.java.spring.agentcheckercode.jules;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import co.com.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.com.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.com.japl.java.spring.agentcheckercode.jules.dto.JulesTaskResponse;
import co.com.japl.java.spring.agentcheckercode.repository.RepositoryWorkspaceService;
import co.com.japl.java.spring.agentcheckercode.stitch.StitchApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueDelegationServiceImplTest {

    @Mock
    private RepositoriesConfigProperties repositoriesConfig;
    @Mock
    private GitHubClientService gitHubClientService;
    @Mock
    private JulesApiClient julesApiClient;
    @Mock
    private StitchApiClient stitchApiClient;
    @Mock
    private RepositoryWorkspaceService repositoryWorkspaceService;

    private IssueDelegationServiceImpl delegationService;

    @BeforeEach
    void setUp() {
        delegationService = new IssueDelegationServiceImpl(
                repositoriesConfig,
                gitHubClientService,
                julesApiClient,
                stitchApiClient,
                repositoryWorkspaceService
        );
    }

    @Test
    void delegateApiCalls_WhenServicesNotConfigured_Skipped() {
        when(gitHubClientService.isConfigured()).thenReturn(false);

        RepositoryConfig repo = new RepositoryConfig();
        repo.setName("test-repo");

        delegationService.delegateApiCalls(repo);

        verify(gitHubClientService, never()).getIssues(any(), any(), any());
    }

    @Test
    void delegateApiCalls_WhenIssuesExist_DelegatesDevelopmentIssue() {
        when(gitHubClientService.isConfigured()).thenReturn(true);
        when(julesApiClient.isConfigured()).thenReturn(true);
        when(stitchApiClient.isConfigured()).thenReturn(true);

        RepositoriesConfigProperties.GlobalSettings globalSettings = new RepositoriesConfigProperties.GlobalSettings();
        when(repositoriesConfig.getGlobalSettings()).thenReturn(globalSettings);

        RepositoryConfig repo = new RepositoryConfig();
        repo.setName("test-repo");
        repo.getGithub().setOwner("owner");
        repo.getGithub().setRepo("repo");

        GitHubIssue issue = GitHubIssue.builder()
                .number(1L)
                .title("Fix bug")
                .body("Bug description")
                .labels(List.of(GitHubIssue.Label.builder().name("triage-resolvable-by-agent").build()))
                .build();

        when(gitHubClientService.getIssues("owner", "repo", "open")).thenReturn(List.of(issue));
        when(julesApiClient.delegateIssue(anyString(), eq(1L), anyString(), anyString()))
                .thenReturn(JulesTaskResponse.builder().taskId("task-1").build());

        delegationService.delegateApiCalls(repo);

        verify(julesApiClient).delegateIssue(eq("https://github.com/owner/repo"), eq(1L), eq("main"), anyString());
        verify(gitHubClientService).addIssueComment("owner", "repo", 1L, "Delegated to Jules. Jules task: task-1.");
        verify(gitHubClientService).addIssueLabel("owner", "repo", 1L, "delegated-to-jules");
    }
}
