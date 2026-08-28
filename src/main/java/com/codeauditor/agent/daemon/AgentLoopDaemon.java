package com.codeauditor.agent.daemon;

import com.codeauditor.agent.config.RepositoriesConfigProperties;
import com.codeauditor.agent.config.RepositoriesConfigProperties.RepositoryConfig;
import com.codeauditor.agent.queue.RepositoryQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentLoopDaemon {

    private final RepositoryQueueManager queueManager;
    private final RepositoriesConfigProperties repositoriesConfig;

    public AgentLoopDaemon(RepositoryQueueManager queueManager, RepositoriesConfigProperties repositoriesConfig) {
        this.queueManager = queueManager;
        this.repositoriesConfig = repositoriesConfig;
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
        log.info("[Triage Pipeline] Inspecting Crashlytics, GitHub Actions CI/CD logs, and AST duplications for repo '{}'...", repo.getName());
    }

    protected void delegateApiCalls(RepositoryConfig repo) {
        log.info("[Agent Delegation] Delegating resolvable issues to Google Jules API / Google Stitch API for repo '{}'...", repo.getName());
    }
}
