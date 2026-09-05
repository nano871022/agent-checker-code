package co.com.japl.java.spring.agentcheckercode.triage;

import co.com.japl.java.spring.agentcheckercode.github.dto.CreateIssueRequest;
import co.com.japl.java.spring.agentcheckercode.triage.dto.TriageResult;

public interface FindingPublisherService {
    int publishFinding(String owner, String repo, CreateIssueRequest request, TriageResult result, String findingId);
    void addTriageComment(String owner, String repo, Long issueNumber, TriageResult result);
}
