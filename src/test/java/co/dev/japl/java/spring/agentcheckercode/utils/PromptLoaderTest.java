package co.dev.japl.java.spring.agentcheckercode.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptLoaderTest {

    @Test
    void testLoadPrompt_ExistingPromptFile() {
        String content = PromptLoader.loadPrompt("prompts/crash-analysis-system.prompt");
        assertNotNull(content);
        assertTrue(content.contains("You are a Tech Lead & Code Auditor AI Triage Assistant."));
    }

    @Test
    void testLoadPrompt_PromtFallback() {
        String content = PromptLoader.loadPrompt("prompts/crash-analysis-system.promt");
        assertNotNull(content);
        assertTrue(content.contains("You are a Tech Lead & Code Auditor AI Triage Assistant."));
    }

    @Test
    void testRenderPrompt_Success() {
        String rendered = PromptLoader.renderPrompt("prompts/triage-comment.prompt", Map.of(
                "decision", "RESOLVABLE_BY_AGENT",
                "detailedAnalysis", "Analysis text"
        ));
        assertNotNull(rendered);
        assertTrue(rendered.contains("**Decision:** RESOLVABLE_BY_AGENT"));
        assertTrue(rendered.contains("Analysis text"));
    }

    @Test
    void testLoadPrompt_NonExistentFile_ThrowsException() {
        assertThrows(IllegalStateException.class, () -> PromptLoader.loadPrompt("prompts/non-existent.prompt"));
    }
}
