package co.dev.japl.java.spring.agentcheckercode.utils;

import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PromptLoader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    public static String loadPrompt(String resourcePath) {
        return CACHE.computeIfAbsent(resourcePath, path -> {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    String altPath = path.endsWith(".prompt")
                            ? path.substring(0, path.length() - ".prompt".length()) + ".promt"
                            : (path.endsWith(".promt") ? path.substring(0, path.length() - ".promt".length()) + ".prompt" : path);
                    resource = new ClassPathResource(altPath);
                }
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load prompt resource: " + resourcePath, e);
            }
        });
    }

    public static String renderPrompt(String resourcePath, Map<String, Object> variables) {
        String templateText = loadPrompt(resourcePath);
        return PromptTemplate.from(templateText).apply(variables).text();
    }
}
