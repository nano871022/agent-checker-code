package com.codeauditor.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@PropertySource(value = "classpath:agent.yml", factory = YamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties
public class AgentConfigProperties {

    private AgentInfo agent = new AgentInfo();
    private String activeProfile;
    private Map<String, ProfileConfig> profiles = new HashMap<>();

    @Data
    public static class AgentInfo {
        private String name;
        private String version;
    }

    @Data
    public static class ProfileConfig {
        private String provider;
        private String baseUrl;
        private String model;
        private Double temperature;
        private String apiKeyEnv;
        private String apiKey;
    }
}
