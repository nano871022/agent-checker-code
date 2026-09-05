package co.dev.japl.java.spring.agentcheckercode.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubWorkflowRunsResponse {
    @JsonProperty("total_count")
    private Integer totalCount;
    @JsonProperty("workflow_runs")
    private List<GitHubWorkflowRun> workflowRuns;
}
