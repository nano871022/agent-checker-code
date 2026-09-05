package com.codeauditor.agent.jules;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

import com.codeauditor.agent.jules.dto.JulesTaskRequest;
import com.codeauditor.agent.jules.dto.JulesTaskResponse;

class JulesApiClientTest {

    private MockRestServiceServer mockServer;
    private JulesApiClient julesApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        julesApiClient = new JulesApiClient(builder, "https://jules.googleapis.com/v1alpha", "test-api-key");
    }

    @Test
    void delegateTask_shouldPostTaskAndReturnResponse() {
        String jsonResponse = """
                {
                  "task_id": "task-123",
                  "status": "IN_PROGRESS",
                  "target_branch": "fix/crash-v1.0.0-abc1234",
                  "message": "Task queued successfully"
                }
                """;

        mockServer.expect(requestTo("https://jules.googleapis.com/v1alpha/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        JulesTaskRequest request = JulesTaskRequest.builder()
                .prompt("Fix NullPointerException in UserActivity.java")
                .title("Fix issue")
                .automationMode("AUTO_CREATE_PR")
                .sourceContext(JulesTaskRequest.SourceContext.builder()
                        .source("sources/github/owner/repo")
                        .githubRepoContext(JulesTaskRequest.GithubRepoContext.builder()
                                .startingBranch("main").build())
                        .build())
                .build();

        JulesTaskResponse response = julesApiClient.delegateTask(request);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("task-123");
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getTargetBranch()).isEqualTo("fix/crash-v1.0.0-abc1234");
        assertThat(response.getMessage()).isEqualTo("Task queued successfully");

        mockServer.verify();
    }

    @Test
    void delegateIssue_shouldBuildRequestAndPostTask() {
        String jsonResponse = """
                {
                  "task_id": "task-456",
                  "status": "CREATED",
                  "target_branch": "fix/crash-v2.0.0-xyz9876"
                }
                """;

        mockServer.expect(requestTo("https://jules.googleapis.com/v1alpha/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        JulesTaskResponse response = julesApiClient.delegateIssue(
                "https://github.com/owner/repo",
                100L,
                "main"
        );

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("task-456");
        assertThat(response.getStatus()).isEqualTo("CREATED");
        assertThat(response.getTargetBranch()).isEqualTo("fix/crash-v2.0.0-xyz9876");

        mockServer.verify();
    }

    @Test
    void getTaskStatus_shouldFetchTaskById() {
        String jsonResponse = """
                {
                  "task_id": "task-123",
                  "status": "COMPLETED",
                  "target_branch": "fix/crash-v1.0.0-abc1234",
                  "pull_request_url": "https://github.com/owner/repo/pull/5"
                }
                """;

        mockServer.expect(requestTo("https://jules.googleapis.com/v1alpha/sessions/task-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        JulesTaskResponse response = julesApiClient.getTaskStatus("task-123");

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("task-123");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getPullRequestUrl()).isEqualTo("https://github.com/owner/repo/pull/5");

        mockServer.verify();
    }
}
