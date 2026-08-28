package com.codeauditor.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PrioritizeResponse(
        boolean success,
        String message,
        @JsonProperty("repository_name")
        String repositoryName
) {}
