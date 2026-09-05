package co.dev.japl.java.spring.agentcheckercode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CodeAuditorAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAuditorAgentApplication.class, args);
    }
}
