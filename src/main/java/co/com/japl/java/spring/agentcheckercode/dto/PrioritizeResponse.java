package co.com.japl.java.spring.agentcheckercode.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PrioritizeResponse(
        boolean success,
        String message,
        @JsonProperty("repository_name")
        String repositoryName
) {}
