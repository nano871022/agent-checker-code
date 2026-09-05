package co.com.japl.java.spring.agentcheckercode.jules;

import co.com.japl.java.spring.agentcheckercode.config.RepositoriesConfigProperties.RepositoryConfig;

public interface IssueDelegationService {
    void delegateApiCalls(RepositoryConfig repo);
}
