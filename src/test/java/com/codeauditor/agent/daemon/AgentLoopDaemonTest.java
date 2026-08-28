package com.codeauditor.agent.daemon;

import com.codeauditor.agent.config.RepositoriesConfigProperties;
import com.codeauditor.agent.config.RepositoriesConfigProperties.RepositoryConfig;
import com.codeauditor.agent.queue.RepositoryQueueManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopDaemonTest {

    @Mock
    private RepositoryQueueManager queueManager;

    private RepositoriesConfigProperties repositoriesConfig;
    private AgentLoopDaemon agentLoopDaemon;

    @BeforeEach
    void setUp() {
        repositoriesConfig = new RepositoriesConfigProperties();
        agentLoopDaemon = new AgentLoopDaemon(queueManager, repositoriesConfig);
    }

    @Test
    void processNextInQueue_WhenAutoTriageEnabled_ExecutesPipeline() {
        repositoriesConfig.getGlobalSettings().setAutoTriageEnabled(true);
        RepositoryConfig repo = new RepositoryConfig();
        repo.setName("test-repo");

        when(queueManager.pollNextRepository()).thenReturn(repo);

        boolean result = agentLoopDaemon.processNextInQueue();

        assertTrue(result);
        verify(queueManager, times(1)).pollNextRepository();
    }

    @Test
    void processNextInQueue_WhenAutoTriageDisabled_SkipsExecution() {
        repositoriesConfig.getGlobalSettings().setAutoTriageEnabled(false);

        boolean result = agentLoopDaemon.processNextInQueue();

        assertFalse(result);
        verify(queueManager, never()).pollNextRepository();
    }

    @Test
    void processNextInQueue_WhenQueueIsEmpty_ReturnsFalse() {
        repositoriesConfig.getGlobalSettings().setAutoTriageEnabled(true);
        when(queueManager.pollNextRepository()).thenReturn(null);

        boolean result = agentLoopDaemon.processNextInQueue();

        assertFalse(result);
        verify(queueManager, times(1)).pollNextRepository();
    }

    @Test
    void processNextInQueue_WhenPipelineThrowsException_HandlesGracefully() {
        repositoriesConfig.getGlobalSettings().setAutoTriageEnabled(true);
        RepositoryConfig repo = new RepositoryConfig();
        repo.setName("failing-repo");

        when(queueManager.pollNextRepository()).thenReturn(repo);

        AgentLoopDaemon failingDaemon = new AgentLoopDaemon(queueManager, repositoriesConfig) {
            @Override
            protected void executeTriagePipeline(RepositoryConfig r) {
                throw new RuntimeException("Triage engine error");
            }
        };

        boolean result = failingDaemon.processNextInQueue();

        assertFalse(result);
        verify(queueManager, times(1)).pollNextRepository();
    }

    @Test
    void runAnalysisLoop_CallsProcessNextInQueueWithoutThrowing() {
        repositoriesConfig.getGlobalSettings().setAutoTriageEnabled(true);
        when(queueManager.pollNextRepository()).thenThrow(new RuntimeException("Unexpected queue error"));

        // Should not throw exception
        agentLoopDaemon.runAnalysisLoop();

        verify(queueManager, times(1)).pollNextRepository();
    }
}
