package co.dev.japl.java.spring.agentcheckercode.queue;

import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class RepositoryQueueManager {

    private final RepositoriesConfigProperties repositoriesConfig;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedDeque<RepositoryConfig> queue = new ConcurrentLinkedDeque<>();
    private final List<RepositoryConfig> enabledRepositories = new ArrayList<>();
    private int currentIndex = -1;

    public RepositoryQueueManager(RepositoriesConfigProperties repositoriesConfig, ObjectMapper objectMapper) {
        this.repositoriesConfig = repositoriesConfig;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void initializeQueue() {
        queue.clear();
        enabledRepositories.clear();

        List<RepositoryConfig> configured = repositoriesConfig.getRepositories();
        if (configured != null) {
            for (RepositoryConfig repo : configured) {
                if (repo.isEnabled()) {
                    enabledRepositories.add(repo);
                }
            }
        }

        if (enabledRepositories.isEmpty()) {
            log.warn("No enabled repositories configured in repositories.yaml");
            return;
        }

        int startIdx = 0;
        AgentState loadedState = loadState();
        if (loadedState != null) {
            int lastIdx = loadedState.getLastProcessedIndex();
            if (lastIdx >= 0 && lastIdx < enabledRepositories.size()) {
                startIdx = (lastIdx + 1) % enabledRepositories.size();
                log.info("Resuming repository queue from index {} based on persisted state.", startIdx);
            }
        }

        for (int i = 0; i < enabledRepositories.size(); i++) {
            int targetIdx = (startIdx + i) % enabledRepositories.size();
            queue.addLast(enabledRepositories.get(targetIdx));
        }

        log.info("RepositoryQueueManager initialized with {} enabled repositories.", queue.size());
    }

    public synchronized RepositoryConfig pollNextRepository() {
        RepositoryConfig repo = queue.pollFirst();
        if (repo == null) {
            return null;
        }

        queue.addLast(repo);

        for (int i = 0; i < enabledRepositories.size(); i++) {
            if (enabledRepositories.get(i).getName().equals(repo.getName())) {
                currentIndex = i;
                break;
            }
        }

        saveState(repo.getName(), currentIndex);
        return repo;
    }

    public RepositoryConfig peekNextRepository() {
        return queue.peekFirst();
    }

    public List<RepositoryConfig> getQueueSnapshot() {
        return new ArrayList<>(queue);
    }

    public synchronized boolean prioritizeRepository(String repositoryName) {
        if (repositoryName == null || repositoryName.isBlank()) {
            return false;
        }

        Optional<RepositoryConfig> target = queue.stream()
                .filter(r -> r.getName().equalsIgnoreCase(repositoryName))
                .findFirst();

        if (target.isPresent()) {
            RepositoryConfig repo = target.get();
            queue.remove(repo);
            queue.addFirst(repo);
            log.info("Repository '{}' prioritized to head of queue.", repositoryName);
            return true;
        } else {
            log.warn("Repository '{}' not found in active queue or disabled.", repositoryName);
            return false;
        }
    }

    private AgentState loadState() {
        String path = repositoriesConfig.getGlobalSettings().getStateFilePath();
        File file = new File(path);
        if (!file.exists()) {
            log.info("State file '{}' does not exist. Starting fresh queue.", path);
            return null;
        }

        try {
            return objectMapper.readValue(file, AgentState.class);
        } catch (IOException e) {
            log.error("Failed to read agent state file '{}': {}", path, e.getMessage());
            return null;
        }
    }

    private void saveState(String repoName, int index) {
        String path = repositoriesConfig.getGlobalSettings().getStateFilePath();
        AgentState state = AgentState.builder()
                .lastProcessedIndex(index)
                .lastProcessedRepositoryName(repoName)
                .lastProcessedTimestamp(Instant.now().toString())
                .build();

        try {
            File file = new File(path);
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.writeValue(file, state);
            log.debug("Agent state saved to '{}': {}", path, state);
        } catch (IOException e) {
            log.error("Failed to save agent state to '{}': {}", path, e.getMessage());
        }
    }
}
