package co.dev.japl.java.spring.agentcheckercode.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubUser {
    private Long id;
    private String login;
    @JsonProperty("avatar_url")
    private String avatarUrl;
}
