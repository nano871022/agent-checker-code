package co.com.japl.java.spring.agentcheckercode.triage;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;

public interface TriagePipelineService {
    void executeTriagePipeline(RepositoryConfig repo);
}
