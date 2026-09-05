package co.com.japl.java.spring.agentcheckercode.daemon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties;
import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;
import co.com.japl.java.spring.agentcheckercode.jules.IssueDelegationService;
import co.com.japl.java.spring.agentcheckercode.queue.RepositoryQueueManager;
import co.com.japl.java.spring.agentcheckercode.triage.TriagePipelineService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentLoopDaemon {

    private final RepositoryQueueManager queueManager;
    private final RepositoriesConfigProperties repositoriesConfig;
    private final TriagePipelineService triagePipelineService;
    private final IssueDelegationService issueDelegationService;

    public AgentLoopDaemon(RepositoryQueueManager queueManager, RepositoriesConfigProperties repositoriesConfig) {
        this(queueManager, repositoriesConfig, null, null);
    }

    @Scheduled(fixedDelayString = "#{repositoriesConfigProperties.globalSettings.checkIntervalMinutes * 60000}", initialDelayString = "${agent.daemon.initial-delay:1000}")
    public void runAnalysisLoop() {
        try {
            processNextInQueue();
        } catch (Exception e) {
            log.error("Unexpected error in agent analysis loop daemon: {}", e.getMessage(), e);
        }
    }

    public boolean processNextInQueue() {
        if (!repositoriesConfig.getGlobalSettings().isAutoTriageEnabled()) {
            log.info("Auto triage is disabled in global settings. Skipping analysis loop execution.");
            return false;
        }

        RepositoryConfig repo = queueManager.pollNextRepository();
        if (repo == null) {
            log.info("No enabled repositories available in queue.");
            return false;
        }

        log.info("Starting analysis loop execution for repository: {}", repo.getName());
        try {
            executeTriagePipeline(repo);
            delegateApiCalls(repo);
            log.info("Successfully completed analysis loop execution for repository: {}", repo.getName());
            return true;
        } catch (Exception e) {
            log.error("Error occurred while processing repository '{}': {}", repo.getName(), e.getMessage(), e);
            return false;
        }
    }

    protected void executeTriagePipeline(RepositoryConfig repo) {
        if (triagePipelineService != null) {
            triagePipelineService.executeTriagePipeline(repo);
        } else {
            log.warn("TriagePipelineService is not wired; repository '{}' was not inspected.", repo.getName());
        }
    }

    protected void delegateApiCalls(RepositoryConfig repo) {
        if (issueDelegationService != null) {
            issueDelegationService.delegateApiCalls(repo);
        } else {
            log.warn("IssueDelegationService is not wired; issue delegation skipped for '{}'.", repo.getName());
        }
    }
}
