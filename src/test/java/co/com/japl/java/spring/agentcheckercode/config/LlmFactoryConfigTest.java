package co.com.japl.java.spring.agentcheckercode.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LlmFactoryConfigTest {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private AgentConfigProperties agentConfigProperties;

    @Test
    void testChatLanguageModelBeanInjection() {
        assertThat(chatLanguageModel).isNotNull();
    }

    @Test
    void testChatLanguageModelCreationWithLocalLmStudio() {
        LlmFactoryConfig factory = new LlmFactoryConfig();
        AgentConfigProperties properties = new AgentConfigProperties();
        properties.setActiveProfile("local_lmstudio");

        AgentConfigProperties.ProfileConfig profile = new AgentConfigProperties.ProfileConfig();
        profile.setProvider("lmstudio");
        profile.setBaseUrl("http://localhost:1234/v1");
        profile.setModel("qwen2.5-coder-32b-instruct");
        profile.setTemperature(0.2);

        Map<String, AgentConfigProperties.ProfileConfig> profiles = new HashMap<>();
        profiles.put("local_lmstudio", profile);
        properties.setProfiles(profiles);

        ChatLanguageModel model = factory.chatLanguageModel(properties);
        assertThat(model).isNotNull();
    }

    @Test
    void testChatLanguageModelCreationWithCloudGeminiFallbackApiKey() {
        LlmFactoryConfig factory = new LlmFactoryConfig();
        AgentConfigProperties properties = new AgentConfigProperties();
        properties.setActiveProfile("cloud_gemini");

        AgentConfigProperties.ProfileConfig profile = new AgentConfigProperties.ProfileConfig();
        profile.setProvider("google");
        profile.setApiKeyEnv("MOCK_GEMINI_API_KEY_NOT_SET");
        profile.setModel("mock-gemini-model");

        Map<String, AgentConfigProperties.ProfileConfig> profiles = new HashMap<>();
        profiles.put("cloud_gemini", profile);
        properties.setProfiles(profiles);

        ChatLanguageModel model = factory.chatLanguageModel(properties);
        assertThat(model).isNotNull();
    }

    @Test
    void testMissingActiveProfileThrowsException() {
        LlmFactoryConfig factory = new LlmFactoryConfig();
        AgentConfigProperties properties = new AgentConfigProperties();
        properties.setActiveProfile(null);

        assertThatThrownBy(() -> factory.chatLanguageModel(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active profile is not specified");
    }

    @Test
    void testUnknownProfileThrowsException() {
        LlmFactoryConfig factory = new LlmFactoryConfig();
        AgentConfigProperties properties = new AgentConfigProperties();
        properties.setActiveProfile("non_existent_profile");

        assertThatThrownBy(() -> factory.chatLanguageModel(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Profile 'non_existent_profile' not found");
    }
}
