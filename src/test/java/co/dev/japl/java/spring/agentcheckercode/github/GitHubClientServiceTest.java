package co.dev.japl.java.spring.agentcheckercode.github;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

import co.dev.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.dev.japl.java.spring.agentcheckercode.github.dto.GitHubIssue;
import co.dev.japl.java.spring.agentcheckercode.github.dto.GitHubWorkflowRunsResponse;

class GitHubClientServiceTest {

    private MockRestServiceServer mockServer;
    private GitHubClientService gitHubClientService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        gitHubClientService = new GitHubClientService(builder, "https://api.github.com", "test-token");
    }

    @Test
    void getIssues_shouldReturnListOfIssues() {
        String jsonResponse = """
                [
                  {
                    "id": 101,
                    "number": 1,
                    "title": "Bug in Auth",
                    "body": "Auth fails unexpectedly",
                    "state": "open",
                    "html_url": "https://github.com/owner/repo/issues/1",
                    "user": {
                      "id": 5,
                      "login": "octocat",
                      "avatar_url": "https://github.com/images/error/octocat_happy.gif"
                    },
                    "labels": [
                      { "id": 10, "name": "bug", "color": "f00", "description": "Bug fix" }
                    ]
                  }
                ]
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/issues?state=open"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<GitHubIssue> issues = gitHubClientService.getIssues("owner", "repo", "open");

        assertThat(issues).hasSize(1);
        GitHubIssue issue = issues.getFirst();
        assertThat(issue.getId()).isEqualTo(101L);
        assertThat(issue.getNumber()).isEqualTo(1L);
        assertThat(issue.getTitle()).isEqualTo("Bug in Auth");
        assertThat(issue.getUser().getLogin()).isEqualTo("octocat");
        assertThat(issue.getLabels()).hasSize(1);
        assertThat(issue.getLabels().getFirst().getName()).isEqualTo("bug");

        mockServer.verify();
    }

    @Test
    void getWorkflowRuns_shouldReturnWorkflowRunsResponse() {
        String jsonResponse = """
                {
                  "total_count": 1,
                  "workflow_runs": [
                    {
                      "id": 201,
                      "name": "CI Build",
                      "head_branch": "main",
                      "head_sha": "abc1234",
                      "status": "completed",
                      "conclusion": "failure",
                      "html_url": "https://github.com/owner/repo/actions/runs/201"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GitHubWorkflowRunsResponse response = gitHubClientService.getWorkflowRuns("owner", "repo");

        assertThat(response).isNotNull();
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getWorkflowRuns()).hasSize(1);
        assertThat(response.getWorkflowRuns().getFirst().getId()).isEqualTo(201L);
        assertThat(response.getWorkflowRuns().getFirst().getConclusion()).isEqualTo("failure");

        mockServer.verify();
    }

    @Test
    void getWorkflowRunLogs_shouldReturnLogString() {
        String mockLogs = "Build failed at line 42: NullPointerException";

        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs/201/logs"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withSuccess(mockLogs, MediaType.TEXT_PLAIN));

        String logs = gitHubClientService.getWorkflowRunLogs("owner", "repo", 201L);

        assertThat(logs).isEqualTo(mockLogs);

        mockServer.verify();
    }

    @Test
    void createIssue_shouldPostAndReturnCreatedIssue() {
        String requestJson = """
                {"title":"Crash in User Service","body":"Detailed stack trace","labels":["bug","crash"],"assignees":null}
                """;
        String jsonResponse = """
                {
                  "id": 102,
                  "number": 2,
                  "title": "Crash in User Service",
                  "body": "Detailed stack trace",
                  "state": "open",
                  "html_url": "https://github.com/owner/repo/issues/2"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
          .andExpect(content().json("""
            {"title":"Crash in User Service","body":"Detailed stack trace",
             "labels":["bug","crash"],"assignees":[]}
            """))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        CreateIssueRequest request = new CreateIssueRequest("Crash in User Service", "Detailed stack trace", List.of("bug", "crash"));
        GitHubIssue createdIssue = gitHubClientService.createIssue("owner", "repo", request);

        assertThat(createdIssue).isNotNull();
        assertThat(createdIssue.getId()).isEqualTo(102L);
        assertThat(createdIssue.getNumber()).isEqualTo(2L);
        assertThat(createdIssue.getTitle()).isEqualTo("Crash in User Service");

        mockServer.verify();
    }

      @Test
      void addIssueComment_shouldPostComment() {
        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/issues/2/comments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().json("{\"body\":\"Automated triage recommendation\"}"))
            .andRespond(withSuccess());

        gitHubClientService.addIssueComment("owner", "repo", 2L, "Automated triage recommendation");

        mockServer.verify();
      }

      @Test
      void addIssueLabel_shouldPostLabel() {
        mockServer.expect(requestTo("https://api.github.com/repos/owner/repo/issues/2/labels"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().json("{\"labels\":[\"delegated-to-jules\"]}"))
            .andRespond(withSuccess());

        gitHubClientService.addIssueLabel("owner", "repo", 2L, "delegated-to-jules");

        mockServer.verify();
      }
}
