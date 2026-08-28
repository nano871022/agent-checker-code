package com.codeauditor.agent.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubWorkflowRun {
    private Long id;
    private String name;
    @JsonProperty("head_branch")
    private String headBranch;
    @JsonProperty("head_sha")
    private String headSha;
    private String status;
    private String conclusion;
    @JsonProperty("html_url")
    private String htmlUrl;
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
    @JsonProperty("logs_url")
    private String logsUrl;
}
