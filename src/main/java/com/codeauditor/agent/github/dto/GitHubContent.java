package com.codeauditor.agent.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubContent {
    private String type;
    private String name;
    private String path;
    private String sha;
    private String content;
    private String encoding;
    @JsonProperty("download_url")
    private String downloadUrl;
    @JsonProperty("html_url")
    private String htmlUrl;
}
