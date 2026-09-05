package co.com.japl.java.spring.agentcheckercode.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PrioritizeRequest(
        @JsonProperty("repository_name")
        @NotBlank(message = "repository_name must not be blank")
        String repositoryName,

        @JsonProperty("force_immediate")
        boolean forceImmediate
) {}
