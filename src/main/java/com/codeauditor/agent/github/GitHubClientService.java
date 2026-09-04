package com.codeauditor.agent.github;

import com.codeauditor.agent.github.dto.CreateIssueRequest;
import com.codeauditor.agent.github.dto.GitHubIssue;
import com.codeauditor.agent.github.dto.GitHubContent;
import com.codeauditor.agent.github.dto.GitHubWorkflowRunsResponse;
import com.codeauditor.agent.github.dto.CreateIssueCommentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Base64;

@Service
public class GitHubClientService {

    private final RestClient restClient;
    private final boolean configured;

    public GitHubClientService(
            RestClient.Builder restClientBuilder,
                @Value("${github.api.base-url:${GITHUB_API_BASE_URL:https://api.github.com}}") String baseUrl,
            @Value("${github.api.token:${GITHUB_TOKEN:${GITHUB_PERSONAL_ACCESS_TOKEN:}}}") String token) {

        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        this.restClient = builder.build();
        this.configured = token != null && !token.isBlank();
    }

    public boolean isConfigured() {
        return configured;
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

    public GitHubContent getRepositoryFile(String owner, String repo, String path) {
        return restClient.get()
            .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .retrieve()
                .body(GitHubContent.class);
    }

    public String decodeRepositoryFile(GitHubContent content) {
        if (content == null || content.getContent() == null) {
            return "";
        }
        if (!"base64".equalsIgnoreCase(content.getEncoding())) {
            return content.getContent();
        }
        return new String(Base64.getMimeDecoder().decode(content.getContent()), StandardCharsets.UTF_8);
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
        byte[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/logs", owner, repo, runId)
                .retrieve()
                .body(byte[].class);
        if (response == null || response.length == 0) {
            return "";
        }
        if (response.length < 2 || response[0] != 'P' || response[1] != 'K') {
            return new String(response, StandardCharsets.UTF_8);
        }
        return unzipLogs(response);
    }

    private String unzipLogs(byte[] response) {
        StringBuilder logs = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    content.write(buffer, 0, read);
                }
                logs.append("--- ").append(entry.getName()).append(" ---\n")
                        .append(content.toString(StandardCharsets.UTF_8))
                        .append("\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to decompress GitHub Actions logs", e);
        }
        return logs.toString();
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

    public void addIssueComment(String owner, String repo, long issueNumber, String comment) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateIssueCommentRequest(comment))
                .retrieve()
                .toBodilessEntity();
    }

    public void addIssueLabel(String owner, String repo, long issueNumber, String label) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}/labels", owner, repo, issueNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("labels", List.of(label)))
                .retrieve()
                .toBodilessEntity();
    }
}
