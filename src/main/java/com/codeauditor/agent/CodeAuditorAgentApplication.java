package com.codeauditor.agent;

import com.codeauditor.agent.config.AgentConfigProperties;
import com.codeauditor.agent.config.RepositoriesConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentConfigProperties.class, RepositoriesConfigProperties.class})
public class CodeAuditorAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAuditorAgentApplication.class, args);
    }
}
