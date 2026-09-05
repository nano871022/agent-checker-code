package co.com.japl.java.spring.agentcheckercode.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConfigurationPropertiesTest {

    @Autowired
    private AgentConfigProperties agentConfigProperties;

    @Autowired
    private RepositoriesConfigProperties repositoriesConfigProperties;

    @Test
    void testAgentConfigPropertiesMapping() {
        assertThat(agentConfigProperties).isNotNull();
        assertThat(agentConfigProperties.getAgent().getName()).isEqualTo("CodeAuditorAgentTest");
        assertThat(agentConfigProperties.getAgent().getVersion()).isEqualTo("1.0.0-test");
        assertThat(agentConfigProperties.getActiveProfile()).isEqualTo("test_local_lmstudio");

        assertThat(agentConfigProperties.getProfiles()).containsKey("test_local_lmstudio");
        AgentConfigProperties.ProfileConfig lmstudioProfile = agentConfigProperties.getProfiles().get("test_local_lmstudio");
        assertThat(lmstudioProfile.getProvider()).isEqualTo("lmstudio");
        assertThat(lmstudioProfile.getBaseUrl()).isEqualTo("http://localhost:1234/v1");
        assertThat(lmstudioProfile.getModel()).isEqualTo("mock-model");
        assertThat(lmstudioProfile.getTemperature()).isEqualTo(0.7);

        assertThat(agentConfigProperties.getProfiles()).containsKey("mock_gemini");
        AgentConfigProperties.ProfileConfig geminiProfile = agentConfigProperties.getProfiles().get("mock_gemini");
        assertThat(geminiProfile.getProvider()).isEqualTo("google");
        assertThat(geminiProfile.getApiKeyEnv()).isEqualTo("MOCK_GEMINI_API_KEY");

        assertThat(agentConfigProperties.getProfiles()).containsKey("mock_anthropic");
        AgentConfigProperties.ProfileConfig anthropicProfile = agentConfigProperties.getProfiles().get("mock_anthropic");
        assertThat(anthropicProfile.getProvider()).isEqualTo("anthropic");
        assertThat(anthropicProfile.getApiKeyEnv()).isEqualTo("MOCK_ANTHROPIC_API_KEY");
    }

    @Test
    void testRepositoriesConfigPropertiesMapping() {
        assertThat(repositoriesConfigProperties).isNotNull();
        assertThat(repositoriesConfigProperties.getGlobalSettings().getCheckIntervalMinutes()).isEqualTo(30);
        assertThat(repositoriesConfigProperties.getGlobalSettings().isAutoTriageEnabled()).isFalse();

        assertThat(repositoriesConfigProperties.getRepositories()).hasSize(1);
        RepositoriesConfigProperties.RepositoryConfig repo = repositoriesConfigProperties.getRepositories().get(0);
        assertThat(repo.getName()).isEqualTo("mobile-app-service");
        assertThat(repo.isEnabled()).isTrue();
        assertThat(repo.getPackageName()).isEqualTo("com.company.app");

        assertThat(repo.getGithub().getOwner()).isEqualTo("your-organization");
        assertThat(repo.getGithub().getRepo()).isEqualTo("mobile-app");
        assertThat(repo.getGithub().getDefaultBranch()).isEqualTo("main");
        assertThat(repo.getGithub().getIssueLabels()).containsExactly("agent-autofix", "crashlytics");

        assertThat(repo.getGithubActions().isEnabled()).isTrue();
        assertThat(repo.getGithubActions().getWorkflowsToMonitor()).containsExactly("ci.yml", "lint-and-test.yml");

        assertThat(repo.getCrashlytics().isEnabled()).isTrue();
        assertThat(repo.getCrashlytics().getAppId()).isEqualTo("1:1234567890:android:abcdef123456");
        assertThat(repo.getCrashlytics().getMinErrorThreshold()).isEqualTo(3);

        assertThat(repo.getStitch().isEnabled()).isTrue();
        assertThat(repo.getStitch().getThemeConfigPath()).isEqualTo("src/theme/material-theme.json");
    }
}
