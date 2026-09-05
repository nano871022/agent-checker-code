package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;

public interface TriagePipelineService {
    void executeTriagePipeline(RepositoryConfig repo);
}
