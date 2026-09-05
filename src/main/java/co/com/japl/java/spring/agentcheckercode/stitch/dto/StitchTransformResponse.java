package co.com.japl.java.spring.agentcheckercode.stitch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StitchTransformResponse {

    @JsonProperty("transformed_code")
    private String transformedCode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("valid")
    private Boolean valid;

    @JsonProperty("error_message")
    private String errorMessage;
}
