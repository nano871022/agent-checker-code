package com.codeauditor.agent.github.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {
    private String title;
    private String body;
    private List<String> labels;
    @Builder.Default
    private List<String> assignees = List.of();

    public CreateIssueRequest(String title, String body) {
        this.title = title;
        this.body = body;
        this.assignees = List.of();
    }

    public CreateIssueRequest(String title, String body, List<String> labels) {
        this.title = title;
        this.body = body;
        this.labels = labels;
        this.assignees = List.of();
    }
}
