package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.GlobalSettings;
import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import co.dev.japl.java.spring.agentcheckercode.crashlytics.FirebaseCrashlyticsService;
import co.dev.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.dev.japl.java.spring.agentcheckercode.github.GitHubClientService;
import co.dev.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.dev.japl.java.spring.agentcheckercode.repository.RepositoryWorkspaceService;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageDecision;
import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriagePipelineServiceImplTest {

    @Mock
    private RepositoriesConfigProperties repositoriesConfig;
    @Mock
    private FirebaseCrashlyticsService crashlyticsService;
    @Mock
    private GitHubClientService gitHubClientService;
    @Mock
    private TriageService triageService;
    @Mock
    private IssueTemplateBuilder issueTemplateBuilder;
    @Mock
    private RepositoryWorkspaceService repositoryWorkspaceService;
    @Mock
    private FindingPublisherService findingPublisherService;

    private TriagePipelineServiceImpl pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new TriagePipelineServiceImpl(
                repositoriesConfig,
                crashlyticsService,
                gitHubClientService,
                triageService,
                issueTemplateBuilder,
                repositoryWorkspaceService,
                findingPublisherService
        );
    }

    @Test
    void executeTriagePipeline_WithCrashlytics_HandlesCrashAndPublishesFinding() {
        RepositoryConfig repo = new RepositoryConfig();
        repo.setName("test-repo");
        repo.getGithub().setOwner("owner");
        repo.getGithub().setRepo("repo");
        repo.getCrashlytics().setEnabled(true);
        repo.getCrashlytics().setAppId("app-123");
        repo.getCrashlytics().setMinErrorThreshold(3);

        GlobalSettings globalSettings = new GlobalSettings();
        when(repositoriesConfig.getGlobalSettings()).thenReturn(globalSettings);
        when(gitHubClientService.isConfigured()).thenReturn(true);

        CrashEvent crash = CrashEvent.builder().crashId("crash-1").build();
        when(crashlyticsService.getUnresolvedCrashes("app-123", null, 3)).thenReturn(List.of(crash));

        TriageResult result = new TriageResult(TriageDecision.RESOLVABLE_BY_AGENT, "NPE fix");
        when(triageService.triageCrashEvent("owner", "repo", crash)).thenReturn(result);

        CreateIssueRequest request = CreateIssueRequest.builder().title("Crash Issue").build();
        when(issueTemplateBuilder.buildCrashIssueRequest(crash)).thenReturn(request);

        pipelineService.executeTriagePipeline(repo);

        verify(findingPublisherService).publishFinding("owner", "repo", request, result, "crash-1");
    }
}
