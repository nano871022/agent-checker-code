package co.com.japl.java.spring.agentcheckercode.jules;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import co.com.japl.java.spring.agentcheckercode.jules.dto.JulesTaskRequest;
import co.com.japl.java.spring.agentcheckercode.jules.dto.JulesTaskResponse;

@Service
public class JulesApiClient {

    private final RestClient restClient;
    private final boolean configured;

    public JulesApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${jules.api.base-url:https://jules.googleapis.com/v1alpha}") String baseUrl,
            @Value("${jules.api.api-key:${JULES_API_KEY:${GOOGLE_JULES_API_KEY:}}}") String apiKey) {

        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-Goog-Api-Key", apiKey);
        }

        this.restClient = builder.build();
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Delegates a GitHub issue / task to Google Jules API.
     *
     * @param request the Jules task request containing repository URL, issue ID, and target branch name
     * @return response from Jules API
     */
    public JulesTaskResponse delegateTask(JulesTaskRequest request) {
        return restClient.post()
                .uri("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JulesTaskResponse.class);
    }

    /**
     * Delegates a GitHub issue to Google Jules API using explicit parameters.
     *
     * @param repositoryUrl full URL of target GitHub repository
     * @param issueId issue ID or number
     * @param targetBranch target branch name (e.g., fix/crash-v1.0.0-abc1234)
     * @return response from Jules API
     */
    public JulesTaskResponse delegateIssue(String repositoryUrl, Long issueId, String targetBranch) {
        return delegateIssue(repositoryUrl, issueId, targetBranch, null);
    }

    public JulesTaskResponse delegateIssue(String repositoryUrl, Long issueId, String targetBranch, String prompt) {
        URI repository = URI.create(repositoryUrl);
        String[] segments = repository.getPath().split("/");
        if (segments.length < 3) {
            throw new IllegalArgumentException("Repository URL must contain owner and repository: " + repositoryUrl);
        }
        String owner = segments[segments.length - 2];
        String repo = segments[segments.length - 1];
        JulesTaskRequest request = JulesTaskRequest.builder()
            .prompt((prompt == null ? "" : prompt) + "\n\nGitHub issue number: #" + issueId)
            .title("Work on GitHub issue #" + issueId)
            .automationMode("AUTO_CREATE_PR")
            .sourceContext(JulesTaskRequest.SourceContext.builder()
                .source("sources/github/" + owner + "/" + repo)
                .githubRepoContext(JulesTaskRequest.GithubRepoContext.builder()
                    .startingBranch(targetBranch == null || targetBranch.isBlank() ? "main" : targetBranch)
                    .build())
                .build())
                .build();
        return delegateTask(request);
    }

    /**
     * Query the status of a Jules task by ID.
     *
     * @param taskId the task ID
     * @return task response containing status
     */
    public JulesTaskResponse getTaskStatus(String taskId) {
        return restClient.get()
                .uri("/sessions/{taskId}", taskId)
                .retrieve()
                .body(JulesTaskResponse.class);
    }
}
