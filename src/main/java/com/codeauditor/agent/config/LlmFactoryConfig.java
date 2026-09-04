package com.codeauditor.agent.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

@Configuration
public class LlmFactoryConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmFactoryConfig.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(AgentConfigProperties agentConfigProperties) {
        String activeProfileName = agentConfigProperties.getActiveProfile();
        if (!StringUtils.hasText(activeProfileName)) {
            throw new IllegalStateException("Active profile is not specified in agent configuration");
        }

        AgentConfigProperties.ProfileConfig profile = agentConfigProperties.getProfiles().get(activeProfileName);
        if (profile == null) {
            throw new IllegalStateException("Profile '" + activeProfileName + "' not found in agent configuration profiles");
        }

        String apiKey = resolveApiKey(profile);
        String baseUrl = profile.getBaseUrl();
        String modelName = StringUtils.hasText(profile.getModel()) ? profile.getModel() : "gpt-3.5-turbo";

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
            .modelName(modelName);

        if ("lmstudio".equalsIgnoreCase(profile.getProvider())) {
            builder.timeout(Duration.ofHours(3));
        }

        if (StringUtils.hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }

        if (profile.getTemperature() != null) {
            builder.temperature(profile.getTemperature());
        }

        log.info("LLM configured with profile='{}', provider='{}', baseUrl='{}', model='{}'",
            activeProfileName, profile.getProvider(), baseUrl, modelName);

        return builder.build();
    }

    private String resolveApiKey(AgentConfigProperties.ProfileConfig profile) {
        if (StringUtils.hasText(profile.getApiKeyEnv())) {
            String envValue = System.getenv(profile.getApiKeyEnv());
            if (StringUtils.hasText(envValue)) {
                return envValue;
            }
        }
        if (StringUtils.hasText(profile.getApiKey())) {
            return profile.getApiKey();
        }
        // Fallback dummy API key for local or unauthenticated OpenAI-compatible servers (e.g., LM Studio)
        return "lm-studio";
    }
}
