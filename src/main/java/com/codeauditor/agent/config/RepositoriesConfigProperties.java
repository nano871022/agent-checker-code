package com.codeauditor.agent.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import lombok.Data;

@Data
@Configuration
@PropertySource(value = "classpath:repositories.yaml", factory = YamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties
public class RepositoriesConfigProperties {

    private GlobalSettings globalSettings = new GlobalSettings();
    private List<RepositoryConfig> repositories = new ArrayList<>();

    @Data
    public static class GlobalSettings {
        private int checkIntervalMinutes = 30;
        private boolean autoTriageEnabled = true;
        private String stateFilePath = "./.agent-state.json";
        private String repositoriesBasePath = "./repositories";
    }

    @Data
    public static class RepositoryConfig {
        private String name;
        private boolean enabled = true;
        private String packageName;
        private GithubConfig github = new GithubConfig();
        private GithubActionsConfig githubActions = new GithubActionsConfig();
        private CrashlyticsConfig crashlytics = new CrashlyticsConfig();
        private StitchConfig stitch = new StitchConfig();
        private AuditConfig audit = new AuditConfig();
    }

    @Data
    public static class GithubConfig {
        private String owner;
        private String repo;
        private String defaultBranch;
        private List<String> issueLabels = new ArrayList<>();
    }

    @Data
    public static class GithubActionsConfig {
        private boolean enabled = true;
        private List<String> workflowsToMonitor = new ArrayList<>();
    }

    @Data
    public static class CrashlyticsConfig {
        private boolean enabled = true;
        private String appId;
        private int minErrorThreshold = 1;
    }

    @Data
    public static class StitchConfig {
        private boolean enabled = true;
        private String themeConfigPath;
        private String uiComponentsDir;
    }

    @Data
    public static class AuditConfig {
        private boolean enabled = true;
        private List<String> repositoryFiles = new ArrayList<>(List.of(
                ".github/workflows/lint.yml",
                ".github/workflows/test.yml"
        ));
    }
}
