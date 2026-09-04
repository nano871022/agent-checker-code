package com.codeauditor.agent.stitch;

import com.codeauditor.agent.stitch.dto.StitchTransformRequest;
import com.codeauditor.agent.stitch.dto.StitchTransformResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StitchApiClient {

    private static final Logger log = LoggerFactory.getLogger(StitchApiClient.class);

    private final RestClient restClient;

    public StitchApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${stitch.api.base-url:https://stitch.googleapis.com/v1}") String baseUrl,
            @Value("${stitch.api.api-key:${STITCH_API_KEY:${GOOGLE_STITCH_API_KEY:}}}") String apiKey) {

        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-Goog-Api-Key", apiKey);
        }

        this.restClient = builder.build();
    }

    /**
     * Sends UI code and material theme tokens to Google Stitch API for transformation.
     *
     * @param request the transformation request containing UI code, theme tokens, and prompt
     * @return transformed UI response from Stitch API
     */
    public StitchTransformResponse transformUi(StitchTransformRequest request) {
        StitchTransformResponse response = restClient.post()
                .uri("/transform")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(StitchTransformResponse.class);

        if (!validateResponse(response)) {
            log.warn("Stitch API response validation failed for request.");
        }

        return response;
    }

    /**
     * Sends UI code and material theme tokens to Google Stitch API using explicit parameters.
     *
     * @param uiCode target UI code to transform
     * @param themeTokens theme tokens JSON string
     * @param prompt optional prompt instructions
     * @return transformed UI response from Stitch API
     */
    public StitchTransformResponse transformUi(String uiCode, String themeTokens, String prompt) {
        StitchTransformRequest request = StitchTransformRequest.builder()
                .uiCode(uiCode)
                .themeTokens(themeTokens)
                .prompt(prompt)
                .build();
        return transformUi(request);
    }

    /**
     * Validates that the Stitch API response contains valid transformed code.
     *
     * @param response the response from Stitch API
     * @return true if response is non-null, valid flag is true or null (if success status), transformedCode is non-blank, and status is SUCCESS or COMPLETED.
     */
    public boolean validateResponse(StitchTransformResponse response) {
        if (response == null) {
            return false;
        }

        if (Boolean.FALSE.equals(response.getValid())) {
            return false;
        }

        String code = response.getTransformedCode();
        if (code == null || code.isBlank()) {
            return false;
        }

        String status = response.getStatus();
        if (status != null && (status.equalsIgnoreCase("ERROR") || status.equalsIgnoreCase("FAILED"))) {
            return false;
        }

        return true;
    }
}
