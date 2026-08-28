package com.codeauditor.agent.github;

import com.codeauditor.agent.github.dto.CreateIssueRequest;
import com.codeauditor.agent.github.dto.GitHubIssue;
import com.codeauditor.agent.github.dto.GitHubWorkflowRunsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
public class GitHubClientService {

    private final RestClient restClient;

    public GitHubClientService(
            RestClient.Builder restClientBuilder,
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
            @Value("${github.api.token:${GITHUB_TOKEN:}}") String token) {

        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        this.restClient = builder.build();
    }

    /**
     * Query open or closed issues for a repository.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param state issue state filter (e.g. "open", "closed", "all")
     * @return list of GitHub issues
     */
    public List<GitHubIssue> getIssues(String owner, String repo, String state) {
        URI uri = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/issues")
                .queryParamIfPresent("state", state != null && !state.isBlank() ? java.util.Optional.of(state) : java.util.Optional.empty())
                .buildAndExpand(owner, repo)
                .toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<GitHubIssue>>() {});
    }

    /**
     * Fetch GitHub Actions workflow runs for a repository.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return workflow runs response
     */
    public GitHubWorkflowRunsResponse getWorkflowRuns(String owner, String repo) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs", owner, repo)
                .retrieve()
                .body(GitHubWorkflowRunsResponse.class);
    }

    /**
     * Fetch GitHub Actions run logs redirect or content for a run ID.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param runId workflow run ID
     * @return string content or log response
     */
    public String getWorkflowRunLogs(String owner, String repo, long runId) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/logs", owner, repo, runId)
                .retrieve()
                .body(String.class);
    }

    /**
     * Create a structured GitHub Issue.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param request issue creation request payload
     * @return created GitHub issue
     */
    public GitHubIssue createIssue(String owner, String repo, CreateIssueRequest request) {
        return restClient.post()
                .uri("/repos/{owner}/{repo}/issues", owner, repo)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GitHubIssue.class);
    }
}
