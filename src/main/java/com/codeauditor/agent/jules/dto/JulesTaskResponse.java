package com.codeauditor.agent.jules.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JulesTaskResponse {

    @JsonAlias({"task_id", "id", "name"})
    private String taskId;

    @JsonAlias({"status", "state"})
    private String status;

    @JsonProperty("target_branch")
    private String targetBranch;

    @JsonProperty("pull_request_url")
    private String pullRequestUrl;

    private String message;
}
