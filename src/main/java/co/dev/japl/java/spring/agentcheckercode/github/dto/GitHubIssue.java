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
public class GitHubIssue {
    private Long id;
    private Long number;
    private String title;
    private String body;
    private String state;
    @JsonProperty("html_url")
    private String htmlUrl;
    private GitHubUser user;
    private List<Label> labels;
    @JsonProperty("pull_request")
    private Object pullRequest;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Label {
        private Long id;
        private String name;
        private String color;
        private String description;
    }
}
