package co.com.japl.java.spring.agentcheckercode.controller;

import co.com.japl.java.spring.agentcheckercode.dto.PrioritizeRequest;
import co.com.japl.java.spring.agentcheckercode.queue.RepositoryQueueManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RepositoryQueueManager queueManager;

    @Test
    void prioritizeRepository_Success_Returns200() throws Exception {
        String repoName = "org/target-repo";
        PrioritizeRequest request = new PrioritizeRequest(repoName, true);

        when(queueManager.prioritizeRepository(eq(repoName))).thenReturn(true);

        mockMvc.perform(post("/api/v1/queue/prioritize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.repository_name").value(repoName))
                .andExpect(jsonPath("$.message").value("Repository successfully prioritized to head of queue."));

        verify(queueManager).prioritizeRepository(repoName);
    }

    @Test
    void prioritizeRepository_NotFound_Returns404() throws Exception {
        String repoName = "unknown/repo";
        PrioritizeRequest request = new PrioritizeRequest(repoName, false);

        when(queueManager.prioritizeRepository(eq(repoName))).thenReturn(false);

        mockMvc.perform(post("/api/v1/queue/prioritize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.repository_name").value(repoName))
                .andExpect(jsonPath("$.message").value("Repository not found in active queue or is disabled."));

        verify(queueManager).prioritizeRepository(repoName);
    }

    @Test
    void prioritizeRepository_BlankRepositoryName_Returns400() throws Exception {
        String jsonPayload = """
                {
                    "repository_name": "",
                    "force_immediate": true
                }
                """;

        mockMvc.perform(post("/api/v1/queue/prioritize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest());
    }
}
