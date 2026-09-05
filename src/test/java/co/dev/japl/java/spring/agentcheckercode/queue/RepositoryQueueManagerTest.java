package co.dev.japl.java.spring.agentcheckercode.queue;

import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryQueueManagerTest {

    private RepositoriesConfigProperties configProperties;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private String stateFilePath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        configProperties = new RepositoriesConfigProperties();
        stateFilePath = tempDir.resolve(".agent-state.json").toString();
        configProperties.getGlobalSettings().setStateFilePath(stateFilePath);
    }

    private RepositoryConfig createRepo(String name, boolean enabled) {
        RepositoryConfig repo = new RepositoryConfig();
        repo.setName(name);
        repo.setEnabled(enabled);
        return repo;
    }

    @Test
    void testQueueInitializationAndRoundRobin() {
        RepositoryConfig repo1 = createRepo("repo1", true);
        RepositoryConfig repo2 = createRepo("repo2", true);
        RepositoryConfig repo3 = createRepo("repo3", true);
        configProperties.setRepositories(List.of(repo1, repo2, repo3));

        RepositoryQueueManager queueManager = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager.initializeQueue();

        assertEquals("repo1", queueManager.peekNextRepository().getName());

        RepositoryConfig polled1 = queueManager.pollNextRepository();
        assertEquals("repo1", polled1.getName());

        RepositoryConfig polled2 = queueManager.pollNextRepository();
        assertEquals("repo2", polled2.getName());

        RepositoryConfig polled3 = queueManager.pollNextRepository();
        assertEquals("repo3", polled3.getName());

        // Round robin back to repo1
        RepositoryConfig polled4 = queueManager.pollNextRepository();
        assertEquals("repo1", polled4.getName());
    }

    @Test
    void testStatePersistenceAndResume() throws Exception {
        RepositoryConfig repo1 = createRepo("repo1", true);
        RepositoryConfig repo2 = createRepo("repo2", true);
        RepositoryConfig repo3 = createRepo("repo3", true);
        configProperties.setRepositories(List.of(repo1, repo2, repo3));

        RepositoryQueueManager queueManager1 = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager1.initializeQueue();

        // Process repo1 (index 0)
        RepositoryConfig polled = queueManager1.pollNextRepository();
        assertEquals("repo1", polled.getName());

        // Verify state file exists and contains lastProcessedIndex = 0
        File stateFile = new File(stateFilePath);
        assertTrue(stateFile.exists());
        AgentState state = objectMapper.readValue(stateFile, AgentState.class);
        assertEquals(0, state.getLastProcessedIndex());
        assertEquals("repo1", state.getLastProcessedRepositoryName());

        // Initialize new queue manager instance simulating app restart
        RepositoryQueueManager queueManager2 = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager2.initializeQueue();

        // Should start at repo2 (index (0 + 1) % 3)
        assertEquals("repo2", queueManager2.peekNextRepository().getName());
        assertEquals("repo2", queueManager2.pollNextRepository().getName());
    }

    @Test
    void testPrioritizeRepository() {
        RepositoryConfig repo1 = createRepo("repo1", true);
        RepositoryConfig repo2 = createRepo("repo2", true);
        RepositoryConfig repo3 = createRepo("repo3", true);
        configProperties.setRepositories(List.of(repo1, repo2, repo3));

        RepositoryQueueManager queueManager = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager.initializeQueue();

        // Queue order: repo1, repo2, repo3
        assertTrue(queueManager.prioritizeRepository("repo3"));

        // New order should be repo3, repo1, repo2
        assertEquals("repo3", queueManager.peekNextRepository().getName());
        assertEquals("repo3", queueManager.pollNextRepository().getName());
        assertEquals("repo1", queueManager.pollNextRepository().getName());
    }

    @Test
    void testDisabledRepositoriesIgnored() {
        RepositoryConfig repo1 = createRepo("repo1", true);
        RepositoryConfig repo2 = createRepo("repo2", false);
        RepositoryConfig repo3 = createRepo("repo3", true);
        configProperties.setRepositories(List.of(repo1, repo2, repo3));

        RepositoryQueueManager queueManager = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager.initializeQueue();

        List<RepositoryConfig> snapshot = queueManager.getQueueSnapshot();
        assertEquals(2, snapshot.size());
        assertEquals("repo1", snapshot.get(0).getName());
        assertEquals("repo3", snapshot.get(1).getName());

        assertFalse(queueManager.prioritizeRepository("repo2"));
    }

    @Test
    void testEmptyRepositoryList() {
        configProperties.setRepositories(List.of());

        RepositoryQueueManager queueManager = new RepositoryQueueManager(configProperties, objectMapper);
        queueManager.initializeQueue();

        assertNull(queueManager.peekNextRepository());
        assertNull(queueManager.pollNextRepository());
        assertFalse(queueManager.prioritizeRepository("repo1"));
    }
}
