package com.codeauditor.agent.jules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JulesTaskRequest {

    private String prompt;
    private SourceContext sourceContext;
    private String automationMode;
    private String title;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceContext {
        private String source;
        private GithubRepoContext githubRepoContext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GithubRepoContext {
        private String startingBranch;
    }
}
