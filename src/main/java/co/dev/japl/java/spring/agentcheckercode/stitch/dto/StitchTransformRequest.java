package co.dev.japl.java.spring.agentcheckercode.stitch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StitchTransformRequest {

    @JsonProperty("ui_code")
    private String uiCode;

    @JsonProperty("theme_tokens")
    private String themeTokens;

    @JsonProperty("prompt")
    private String prompt;
}
