package com.codeauditor.agent.github.dto;

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
public class CreateIssueRequest {
    private String title;
    private String body;
    private List<String> labels;
    private List<String> assignees;

    public CreateIssueRequest(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public CreateIssueRequest(String title, String body, List<String> labels) {
        this.title = title;
        this.body = body;
        this.labels = labels;
    }
}
