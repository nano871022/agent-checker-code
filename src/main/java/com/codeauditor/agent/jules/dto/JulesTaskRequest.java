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
public class JulesTaskRequest {

    @JsonProperty("repository_url")
    private String repositoryUrl;

    @JsonProperty("issue_id")
    private Long issueId;

    @JsonProperty("target_branch")
    private String targetBranch;

    private String prompt;
}
