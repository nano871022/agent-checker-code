package com.codeauditor.agent.jules.dto;

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

    @JsonProperty("task_id")
    private String taskId;

    private String status;

    @JsonProperty("target_branch")
    private String targetBranch;

    @JsonProperty("pull_request_url")
    private String pullRequestUrl;

    private String message;
}
