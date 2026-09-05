package co.dev.japl.java.spring.agentcheckercode.stitch;

import co.dev.japl.java.spring.agentcheckercode.stitch.dto.StitchTransformRequest;
import co.dev.japl.java.spring.agentcheckercode.stitch.dto.StitchTransformResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StitchApiClientTest {

    private MockRestServiceServer mockServer;
    private StitchApiClient stitchApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        stitchApiClient = new StitchApiClient(builder, "https://stitch.googleapis.com/v1", "test-stitch-key");
    }

    @Test
    void transformUi_shouldPostRequestAndReturnTransformedResponse() {
        String jsonResponse = """
                {
                  "transformed_code": "<Button android:textColor=\\"#6750A4\\" />",
                  "status": "SUCCESS",
                  "valid": true
                }
                """;

        mockServer.expect(requestTo("https://stitch.googleapis.com/v1/transform"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Goog-Api-Key", "test-stitch-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        StitchTransformRequest request = StitchTransformRequest.builder()
                .uiCode("<Button />")
                .themeTokens("{\"primary\": \"#6750A4\"}")
                .prompt("Apply Material 3 theme")
                .build();

        StitchTransformResponse response = stitchApiClient.transformUi(request);

        assertThat(response).isNotNull();
        assertThat(response.getTransformedCode()).isEqualTo("<Button android:textColor=\"#6750A4\" />");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getValid()).isTrue();
        assertThat(stitchApiClient.validateResponse(response)).isTrue();

        mockServer.verify();
    }

    @Test
    void transformUi_withExplicitParameters_shouldBuildRequestAndPost() {
        String jsonResponse = """
                {
                  "transformed_code": "<TextView android:textColor=\\"#1D1B20\\" />",
                  "status": "SUCCESS",
                  "valid": true
                }
                """;

        mockServer.expect(requestTo("https://stitch.googleapis.com/v1/transform"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Goog-Api-Key", "test-stitch-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        StitchTransformResponse response = stitchApiClient.transformUi(
                "<TextView />",
                "{\"onSurface\": \"#1D1B20\"}",
                "Transform text color"
        );

        assertThat(response).isNotNull();
        assertThat(response.getTransformedCode()).isEqualTo("<TextView android:textColor=\"#1D1B20\" />");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getValid()).isTrue();

        mockServer.verify();
    }

    @Test
    void validateResponse_shouldReturnFalseForInvalidOrEmptyResponse() {
        assertThat(stitchApiClient.validateResponse(null)).isFalse();

        StitchTransformResponse invalidFlagResponse = StitchTransformResponse.builder()
                .transformedCode("some code")
                .status("SUCCESS")
                .valid(false)
                .build();
        assertThat(stitchApiClient.validateResponse(invalidFlagResponse)).isFalse();

        StitchTransformResponse emptyCodeResponse = StitchTransformResponse.builder()
                .transformedCode("   ")
                .status("SUCCESS")
                .valid(true)
                .build();
        assertThat(stitchApiClient.validateResponse(emptyCodeResponse)).isFalse();

        StitchTransformResponse errorStatusResponse = StitchTransformResponse.builder()
                .transformedCode("some code")
                .status("ERROR")
                .valid(true)
                .build();
        assertThat(stitchApiClient.validateResponse(errorStatusResponse)).isFalse();
    }
}
